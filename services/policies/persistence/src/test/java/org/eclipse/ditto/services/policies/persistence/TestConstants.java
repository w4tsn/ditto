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
package org.eclipse.ditto.services.policies.persistence;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.policies.EffectedPermissions;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policies.SubjectType;

/**
 * Defines constants for testing.
 */
public final class TestConstants {

    private TestConstants() {
        throw new AssertionError();
    }

    /**
     * Policy-related test constants.
     */
    public static final class Policy {

        /**
         * A known SubjectType.
         */
        public static final SubjectType SUBJECT_TYPE = SubjectType.newInstance("mySubjectType");

        /**
         * The known "read" permission.
         */
        public static final String PERMISSION_READ = "READ";

        /**
         * The known "write" permission.
         */
        public static final String PERMISSION_WRITE = "WRITE";


        /**
         * All known permissions.
         */
        public static final Iterable<String> PERMISSIONS_ALL = Arrays.asList(PERMISSION_READ, PERMISSION_WRITE);

        public static final EffectedPermissions READ_GRANTED =
                EffectedPermissions.newInstance(Collections.singleton(PERMISSION_READ), Collections.emptySet());
        public static final EffectedPermissions READ_WRITE_REVOKED =
                EffectedPermissions.newInstance( Collections.emptySet(), Arrays.asList(PERMISSION_READ, PERMISSION_WRITE));
        public static final ResourceKey FEATURES_RESOURCE_KEY = ResourceKey.newInstance("thing", "/features");
        public static final ResourceKey NEW_ATTRIBUTE_RESOURCE_KEY = ResourceKey.newInstance("thing",
                "/attribute/new");
        public static final Resource NEW_ATTRIBUTE_RESOURCE = Resource.newInstance(NEW_ATTRIBUTE_RESOURCE_KEY,
                READ_GRANTED);
        public static final Resource FEATURES_RESOURCE = Resource.newInstance(FEATURES_RESOURCE_KEY, READ_WRITE_REVOKED);
        public static final Resource
                MODIFIED_FEATURES_RESOURCE = Resource.newInstance(FEATURES_RESOURCE_KEY, READ_GRANTED);
        public static final Label SUPPORT_LABEL = Label.of("Support");
        public static final SubjectId
                SUPPORT_SUBJECT_ID = SubjectId.newInstance(SubjectIssuer.GOOGLE, UUID.randomUUID().toString());
        public static final Subject SUPPORT_SUBJECT = Subject.newInstance(SUPPORT_SUBJECT_ID);

        public static final SubjectId ADDITIONAL_SUPPORT_SUBJECT_ID = SubjectId.newInstance(SubjectIssuer.GOOGLE,
                UUID.randomUUID().toString());
        public static final Subject ADDITIONAL_SUPPORT_SUBJECT = Subject.newInstance(ADDITIONAL_SUPPORT_SUBJECT_ID);

        public static org.eclipse.ditto.model.policies.Policy policyWithRandomName() {
            return PoliciesModelFactory.newPolicyBuilder(PolicyId.inNamespaceWithRandomName("test"))
                    .forLabel("EndUser")
                    .setSubject(SubjectIssuer.GOOGLE, UUID.randomUUID().toString(), SubjectType.newInstance("type"))
                    .setGrantedPermissions("thing", "/", PERMISSION_READ, PERMISSION_WRITE)
                    .setRevokedPermissions("thing", "/attributes", PERMISSION_WRITE)
                    .forLabel(SUPPORT_LABEL)
                    .setSubject(SUPPORT_SUBJECT)
                    .setRevokedPermissions(FEATURES_RESOURCE_KEY, PERMISSION_READ, PERMISSION_WRITE)
                    .setModified(Instant.now())
                    .build();
        }

        public static org.eclipse.ditto.model.policies.PolicyEntry policyEntryWithLabel(final String label) {
            return PoliciesModelFactory.newPolicyEntry(label,
                    PoliciesModelFactory.newSubjects(Subject.newInstance(SubjectIssuer.GOOGLE,
                            UUID.randomUUID().toString())),
                    PoliciesModelFactory.newResources(Resource.newInstance(ResourceKey.newInstance("thing",
                            "/attributes/custom"),
                            PoliciesModelFactory.newEffectedPermissions(Collections.singleton(PERMISSION_READ),
                                    Collections.singleton(PERMISSION_WRITE))))
            );
        }

        /**
         * The known Policy ID.
         */
        public static final PolicyId POLICY_ID = PolicyId.of("com.example", "testPolicy");

        public static final Label LABEL = Label.of("custom");

        public static final String RESOURCE_TYPE_POLICY = "policy";

        private static final String RESOURCE_TYPE_THING = "thing";

        private static final JsonPointer RESOURCE_PATH = JsonPointer.of("/foo/bar");

        /**
         * A Policy to be used in persistence tests.
         */
        public static final org.eclipse.ditto.model.policies.Policy POLICY = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setSubjectFor(LABEL, SUPPORT_SUBJECT_ID, SUBJECT_TYPE)
                .setGrantedPermissionsFor(LABEL, RESOURCE_TYPE_POLICY, "/", PERMISSION_READ, PERMISSION_WRITE)
                .setGrantedPermissionsFor(LABEL, RESOURCE_TYPE_THING, "/", PERMISSION_READ, PERMISSION_WRITE)
                .setRevokedPermissionsFor(LABEL, RESOURCE_TYPE_THING, RESOURCE_PATH, PERMISSION_WRITE)
                .build();

        private Policy() {
            throw new AssertionError();
        }
    }

}
