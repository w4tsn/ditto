/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import javax.annotation.Nullable;

import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.services.policies.common.config.PolicyConfig;
import org.eclipse.ditto.services.utils.persistentactors.commands.AbstractCommandStrategies;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;

/**
 * Command strategies of {@code PolicyPersistenceActor}.
 */
public final class PolicyCommandStrategies
        extends AbstractCommandStrategies<Command, Policy, PolicyId, Result<PolicyEvent>> {

    @Nullable private static volatile PolicyCommandStrategies instance;
    @Nullable private static volatile CreatePolicyStrategy createPolicyStrategy;

    private PolicyCommandStrategies(final PolicyConfig policyConfig) {
        super(Command.class);

        // Policy level
        addStrategy(new PolicyConflictStrategy(policyConfig));
        addStrategy(new ModifyPolicyStrategy(policyConfig));
        addStrategy(new RetrievePolicyStrategy(policyConfig));
        addStrategy(new DeletePolicyStrategy(policyConfig));

        // Policy Entries
        addStrategy(new ModifyPolicyEntriesStrategy(policyConfig));
        addStrategy(new RetrievePolicyEntriesStrategy(policyConfig));

        // Policy Entry
        addStrategy(new ModifyPolicyEntryStrategy(policyConfig));
        addStrategy(new RetrievePolicyEntryStrategy(policyConfig));
        addStrategy(new DeletePolicyEntryStrategy(policyConfig));

        // Subjects
        addStrategy(new ModifySubjectsStrategy(policyConfig));
        addStrategy(new ModifySubjectStrategy(policyConfig));
        addStrategy(new RetrieveSubjectsStrategy(policyConfig));
        addStrategy(new RetrieveSubjectStrategy(policyConfig));
        addStrategy(new DeleteSubjectStrategy(policyConfig));

        // Resources
        addStrategy(new ModifyResourcesStrategy(policyConfig));
        addStrategy(new ModifyResourceStrategy(policyConfig));
        addStrategy(new RetrieveResourcesStrategy(policyConfig));
        addStrategy(new RetrieveResourceStrategy(policyConfig));
        addStrategy(new DeleteResourceStrategy(policyConfig));

        // Sudo
        addStrategy(new SudoRetrievePolicyStrategy(policyConfig));
        addStrategy(new SudoRetrievePolicyRevisionStrategy(policyConfig));
    }

    /**
     * @param policyConfig the PolicyConfig of the Policy service to apply.
     * @return command strategies for policy persistence actor.
     */
    public static PolicyCommandStrategies getInstance(final PolicyConfig policyConfig) {
        PolicyCommandStrategies localInstance = instance;
        if (null == localInstance) {
            synchronized (PolicyCommandStrategies.class) {
                localInstance = instance;
                if (null == localInstance) {
                    instance = localInstance = new PolicyCommandStrategies(policyConfig);
                }
            }
        }
        return localInstance;
    }

    /**
     * @param policyConfig the PolicyConfig of the Policy service to apply.
     * @return command strategy to create a policy.
     */
    public static CommandStrategy<CreatePolicy, Policy, PolicyId, Result<PolicyEvent>> getCreatePolicyStrategy(
            final PolicyConfig policyConfig) {
        CreatePolicyStrategy localCreatePolicyStrategy = createPolicyStrategy;
        if (null == localCreatePolicyStrategy) {
            synchronized (PolicyCommandStrategies.class) {
                localCreatePolicyStrategy = createPolicyStrategy;
                if (null == localCreatePolicyStrategy) {
                    createPolicyStrategy = localCreatePolicyStrategy = new CreatePolicyStrategy(policyConfig);
                }
            }
        }
        return localCreatePolicyStrategy;
    }

    @Override
    protected Result<PolicyEvent> getEmptyResult() {
        return ResultFactory.emptyResult();
    }

}
