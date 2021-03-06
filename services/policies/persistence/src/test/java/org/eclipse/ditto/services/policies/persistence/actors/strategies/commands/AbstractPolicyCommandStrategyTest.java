/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.policies.persistence.actors.strategies.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.services.policies.persistence.TestConstants;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.services.utils.persistentactors.commands.DefaultContext;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultVisitor;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.policies.PolicyCommandResponse;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.junit.BeforeClass;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Abstract base implementation for unit tests of implementations of {@link org.eclipse.ditto.services.utils.persistentactors.commands.AbstractCommandStrategy}.
 */
public abstract class AbstractPolicyCommandStrategyTest {

    protected static final long NEXT_REVISION = 42L;

    protected static DittoDiagnosticLoggingAdapter logger;

    @BeforeClass
    public static void initTestConstants() {
        logger = Mockito.mock(DittoDiagnosticLoggingAdapter.class);
        Mockito.when(logger.withCorrelationId(Mockito.any(DittoHeaders.class))).thenReturn(logger);
        Mockito.when(logger.withCorrelationId(Mockito.any(WithDittoHeaders.class))).thenReturn(logger);
        Mockito.when(logger.withCorrelationId(Mockito.any(CharSequence.class))).thenReturn(logger);
    }

    protected static CommandStrategy.Context<PolicyId> getDefaultContext() {
        return DefaultContext.getInstance(TestConstants.Policy.POLICY_ID, logger);
    }

    protected static <T extends PolicyEvent<?>> T assertModificationResult(final CommandStrategy underTest,
            @Nullable final Policy policy,
            final Command command,
            final Class<T> expectedEventClass,
            final CommandResponse expectedCommandResponse) {

        return assertModificationResult(underTest, policy, command, expectedEventClass, expectedCommandResponse, false);
    }

    protected static <T extends PolicyEvent<?>, R extends PolicyCommandResponse<R>> void assertModificationResult(
            final CommandStrategy underTest,
            @Nullable final Policy policy,
            final Command command,
            final Class<T> expectedEventClass,
            final Consumer<T> eventSatisfactions,
            final Class<R> expectedResponseClass,
            final Consumer<R> responseSatisfactions) {

        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Result<PolicyEvent> result = applyStrategy(underTest, context, policy, command);

        final ArgumentCaptor<T> event = ArgumentCaptor.forClass(expectedEventClass);
        final ArgumentCaptor<R> response = ArgumentCaptor.forClass(expectedResponseClass);
        final ResultVisitor<PolicyEvent> mock = mock(Dummy.class);
        result.accept(mock);

        verify(mock).onMutation(any(), event.capture(), response.capture(), anyBoolean(), eq(false));
        assertThat(event.getValue()).isInstanceOf(expectedEventClass);
        assertThat(response.getValue()).isInstanceOf(expectedResponseClass);

        assertThat(event.getValue())
                .describedAs("Event satisfactions failed for expected event of type '%s'", expectedEventClass.getSimpleName())
                .satisfies(eventSatisfactions);
        assertThat(response.getValue())
                .describedAs("Response predicate failed for expected response of type '%s'", expectedResponseClass.getSimpleName())
                .satisfies(responseSatisfactions);
    }

    protected static <T extends PolicyEvent<?>> T assertModificationResult(final CommandStrategy underTest,
            @Nullable final Policy policy,
            final Command command,
            final Class<T> expectedEventClass,
            final CommandResponse expectedCommandResponse,
            final boolean becomeDeleted) {

        final CommandStrategy.Context<PolicyId> context = getDefaultContext();
        final Result<PolicyEvent> result = applyStrategy(underTest, context, policy, command);

        return assertModificationResult(result, expectedEventClass, expectedCommandResponse, becomeDeleted);
    }

    protected static <C extends Command> void assertErrorResult(
            final CommandStrategy<C, Policy, PolicyId, Result<PolicyEvent>> underTest,
            @Nullable final Policy policy,
            final C command,
            final DittoRuntimeException expectedException) {

        final ResultVisitor<PolicyEvent> mock = mock(Dummy.class);
        applyStrategy(underTest, getDefaultContext(), policy, command).accept(mock);
        verify(mock).onError(eq(expectedException), eq(command));
    }

    private static <T extends PolicyEvent<?>> T assertModificationResult(final Result<PolicyEvent> result,
            final Class<T> eventClazz,
            final WithDittoHeaders expectedResponse,
            final boolean becomeDeleted) {

        final ArgumentCaptor<T> event = ArgumentCaptor.forClass(eventClazz);

        final ResultVisitor<PolicyEvent> mock = mock(Dummy.class);

        result.accept(mock);

        verify(mock).onMutation(any(), event.capture(), eq(expectedResponse), anyBoolean(), eq(becomeDeleted));
        assertThat(event.getValue()).isInstanceOf(eventClazz);
        return event.getValue();
    }

    private static <C extends Command> Result<PolicyEvent> applyStrategy(
            final CommandStrategy<C, Policy, PolicyId, Result<PolicyEvent>> underTest,
            final CommandStrategy.Context<PolicyId> context,
            @Nullable final Policy policy,
            final C command) {

        return underTest.apply(context, policy, NEXT_REVISION, command);
    }

    interface Dummy extends ResultVisitor<PolicyEvent> {}

    protected static DittoHeaders appendETagToDittoHeaders(final Object object, final DittoHeaders dittoHeaders) {

        return dittoHeaders.toBuilder().eTag(EntityTag.fromEntity(object).get()).build();
    }

}
