/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.actors;

import java.util.function.UnaryOperator;

import javax.annotation.Nullable;
import javax.jms.JMSRuntimeException;
import javax.naming.NamingException;

import org.eclipse.ditto.services.base.actors.DittoRootActor;
import org.eclipse.ditto.services.connectivity.config.ConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.ClientActorPropsFactory;
import org.eclipse.ditto.services.connectivity.messaging.ConnectivityProxyActor;
import org.eclipse.ditto.services.connectivity.messaging.DefaultClientActorPropsFactory;
import org.eclipse.ditto.services.connectivity.messaging.ReconnectActor;
import org.eclipse.ditto.services.connectivity.messaging.persistence.ConnectionPersistenceOperationsActor;
import org.eclipse.ditto.services.connectivity.messaging.persistence.ConnectionPersistenceStreamingActorCreator;
import org.eclipse.ditto.services.connectivity.messaging.persistence.ConnectionSupervisorActor;
import org.eclipse.ditto.services.models.concierge.actors.ConciergeEnforcerClusterRouterFactory;
import org.eclipse.ditto.services.models.concierge.actors.ConciergeForwarderActor;
import org.eclipse.ditto.services.models.connectivity.ConnectivityMessagingConstants;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.cluster.ClusterUtil;
import org.eclipse.ditto.services.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.services.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.services.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.services.utils.health.DefaultHealthCheckingActorFactory;
import org.eclipse.ditto.services.utils.health.HealthCheckingActorOptions;
import org.eclipse.ditto.services.utils.health.config.HealthCheckConfig;
import org.eclipse.ditto.services.utils.health.config.MetricsReporterConfig;
import org.eclipse.ditto.services.utils.health.config.PersistenceConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoHealthChecker;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoMetricsReporter;
import org.eclipse.ditto.services.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommandInterceptor;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.DeciderBuilder;
import scala.PartialFunction;

/**
 * Parent Actor which takes care of supervision of all other Actors in our system.
 */
public final class ConnectivityRootActor extends DittoRootActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "connectivityRoot";

    private static final String CLUSTER_ROLE = "connectivity";

    private final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    @SuppressWarnings("unused")
    private ConnectivityRootActor(final ConnectivityConfig connectivityConfig,
            final ActorRef pubSubMediator,
            final UnaryOperator<Signal<?>> conciergeForwarderSignalTransformer,
            @Nullable final ConnectivityCommandInterceptor commandValidator) {

        final ClusterConfig clusterConfig = connectivityConfig.getClusterConfig();
        final ActorSystem actorSystem = getContext().system();

        final ActorRef conciergeForwarder =
                getConciergeForwarder(clusterConfig, pubSubMediator, conciergeForwarderSignalTransformer);

        final ActorRef proxyActor =
                startChildActor(ConnectivityProxyActor.ACTOR_NAME, ConnectivityProxyActor.props(conciergeForwarder));

        final Props connectionSupervisorProps =
                getConnectionSupervisorProps(proxyActor, commandValidator, pubSubMediator);

        // Create persistence streaming actor (with no cache) and make it known to pubSubMediator.
        final ActorRef persistenceStreamingActor =
                startChildActor(ConnectionPersistenceStreamingActorCreator.ACTOR_NAME,
                        ConnectionPersistenceStreamingActorCreator.props(0));
        pubSubMediator.tell(DistPubSubAccess.put(persistenceStreamingActor), getSelf());

        startClusterSingletonActor(
                ReconnectActor.props(getConnectionShardRegion(actorSystem, connectionSupervisorProps, clusterConfig),
                        MongoReadJournal.newInstance(actorSystem)),
                ReconnectActor.ACTOR_NAME);

        startChildActor(ConnectionPersistenceOperationsActor.ACTOR_NAME,
                ConnectionPersistenceOperationsActor.props(pubSubMediator, connectivityConfig.getMongoDbConfig(),
                        actorSystem.settings().config(), connectivityConfig.getPersistenceOperationsConfig()));

        final ActorRef healthCheckingActor = getHealthCheckingActor(connectivityConfig, pubSubMediator);
        bindHttpStatusRoute(connectivityConfig.getHttpConfig(), healthCheckingActor);
    }

    /**
     * Creates Akka configuration object Props for this ConnectivityRootActor.
     *
     * @param connectivityConfig the configuration of the Connectivity service.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param conciergeForwarderSignalTransformer a function which transforms signals before forwarding them to the
     * concierge service
     * @param commandValidator custom command validator for connectivity commands
     * @return the Akka configuration Props object.
     */
    public static Props props(final ConnectivityConfig connectivityConfig,
            final ActorRef pubSubMediator,
            final UnaryOperator<Signal<?>> conciergeForwarderSignalTransformer,
            final ConnectivityCommandInterceptor commandValidator) {

        return Props.create(ConnectivityRootActor.class, connectivityConfig, pubSubMediator,
                conciergeForwarderSignalTransformer, commandValidator);
    }

    /**
     * Creates Akka configuration object Props for this ConnectivityRootActor.
     *
     * @param connectivityConfig the configuration of the Connectivity service.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param conciergeForwarderSignalTransformer a function which transforms signals before forwarding them to the
     * concierge service
     * @return the Akka configuration Props object.
     */
    public static Props props(final ConnectivityConfig connectivityConfig, final ActorRef pubSubMediator,
            final UnaryOperator<Signal<?>> conciergeForwarderSignalTransformer) {

        return Props.create(ConnectivityRootActor.class, connectivityConfig, pubSubMediator,
                conciergeForwarderSignalTransformer, null);
    }

    @Override
    protected PartialFunction<Throwable, SupervisorStrategy.Directive> getSupervisionDecider() {
        return DeciderBuilder.match(JMSRuntimeException.class, e -> {
            log.warning("JMSRuntimeException '{}' occurred.", e.getMessage());
            return restartChild();
        }).match(NamingException.class, e -> {
            log.warning("NamingException '{}' occurred.", e.getMessage());
            return restartChild();
        }).build().orElse(super.getSupervisionDecider());
    }

    private void startClusterSingletonActor(final Props props, final String name) {
        ClusterUtil.startSingleton(getContext(), CLUSTER_ROLE, name, props);
    }

    private ActorRef getHealthCheckingActor(final ConnectivityConfig connectivityConfig,
            final ActorRef pubSubMediator) {
        final HealthCheckConfig healthCheckConfig = connectivityConfig.getHealthCheckConfig();
        final HealthCheckingActorOptions.Builder hcBuilder =
                HealthCheckingActorOptions.getBuilder(healthCheckConfig.isEnabled(), healthCheckConfig.getInterval());
        final PersistenceConfig persistenceConfig = healthCheckConfig.getPersistenceConfig();
        if (persistenceConfig.isEnabled()) {
            hcBuilder.enablePersistenceCheck();
        }
        final HealthCheckingActorOptions healthCheckingActorOptions = hcBuilder.build();

        final MetricsReporterConfig metricsReporterConfig =
                healthCheckConfig.getPersistenceConfig().getMetricsReporterConfig();
        return startChildActor(DefaultHealthCheckingActorFactory.ACTOR_NAME,
                DefaultHealthCheckingActorFactory.props(healthCheckingActorOptions,
                        MongoHealthChecker.props(),
                        MongoMetricsReporter.props(
                                metricsReporterConfig.getResolution(),
                                metricsReporterConfig.getHistory(),
                                pubSubMediator
                        )
                ));
    }

    private ActorRef getConciergeForwarder(final ClusterConfig clusterConfig, final ActorRef pubSubMediator,
            final UnaryOperator<Signal<?>> conciergeForwarderSignalTransformer) {

        final ActorRef conciergeEnforcerRouter =
                ConciergeEnforcerClusterRouterFactory.createConciergeEnforcerClusterRouter(getContext(),
                        clusterConfig.getNumberOfShards());

        return startChildActor(ConciergeForwarderActor.ACTOR_NAME,
                ConciergeForwarderActor.props(pubSubMediator, conciergeEnforcerRouter,
                        conciergeForwarderSignalTransformer));
    }

    private static Props getConnectionSupervisorProps(final ActorRef proxyActor,
            @Nullable final ConnectivityCommandInterceptor commandValidator,
            final ActorRef pubSubMediator) {

        final ClientActorPropsFactory clientActorPropsFactory = DefaultClientActorPropsFactory.getInstance();

        return ConnectionSupervisorActor.props(proxyActor, clientActorPropsFactory, commandValidator, pubSubMediator);
    }

    private static ActorRef getConnectionShardRegion(final ActorSystem actorSystem,
            final Props connectionSupervisorProps, final ClusterConfig clusterConfig) {

        final ClusterShardingSettings shardingSettings = ClusterShardingSettings.create(actorSystem)
                .withRole(ConnectivityMessagingConstants.CLUSTER_ROLE);

        return ClusterSharding.get(actorSystem)
                .start(ConnectivityMessagingConstants.SHARD_REGION,
                        connectionSupervisorProps,
                        shardingSettings,
                        ShardRegionExtractor.of(clusterConfig.getNumberOfShards(), actorSystem));
    }

}
