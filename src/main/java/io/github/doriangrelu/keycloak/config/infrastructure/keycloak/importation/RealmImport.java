/*-
 * ---license-start
 * keycloak-config-cli
 * ---
 * Copyright (C) 2017 - 2021 adorsys GmbH & Co. KG @ https://adorsys.com
 * ---
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ---license-end
 */

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.importation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ProtectionRule;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.keycloak.representations.idm.AuthenticationFlowRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.userprofile.config.UPConfig;

public class RealmImport extends RealmRepresentation {
    private List<AuthenticationFlowImport> authenticationFlowImports;

    private UPConfig userProfile;

    private Map<String, Map<String, String>> messageBundles;

    private List<ProtectionRule> protectedResources = List.of();

    private String source;

    // FGAP V2 field (Keycloak 26.2+) - Fine-Grained Admin Permissions
    // This field is not in the base RealmRepresentation but is used in newer Keycloak versions
    // When set to true, Keycloak automatically creates an "admin-permissions" client
    private Boolean adminPermissionsEnabled;

    @Override
    @SuppressWarnings("java:S1168")
    public List<AuthenticationFlowRepresentation> getAuthenticationFlows() {
        if (authenticationFlowImports == null) return null;

        return new ArrayList<>(authenticationFlowImports);
    }

    /**
     * A {@code protectedResources} entry of an import file: {@code rule} ({@code EQUALS} by default, or
     * {@code PATTERN}), {@code matcher} and an optional {@code type}, the only resource type the rule protects.
     */
    public record ProtectedResourceDeclaration(ProtectionRule.Matching rule, String matcher, ResourceType type) {}

    /**
     * Converts the declarations to protection rules when the file is read, so that an invalid regular expression fails
     * before anything is changed in Keycloak.
     */
    @SuppressWarnings("unused")
    @JsonSetter("protectedResources")
    public void setProtectedResources(final Collection<ProtectedResourceDeclaration> declarations) {
        this.protectedResources = declarations == null
                ? List.of()
                : declarations.stream()
                        .map(declaration ->
                                new ProtectionRule(declaration.rule(), declaration.matcher(), declaration.type()))
                        .toList();
    }

    @JsonIgnore
    public List<ProtectionRule> getProtectedResources() {
        return this.protectedResources;
    }

    @SuppressWarnings("unused")
    @JsonSetter("authenticationFlows")
    public void setAuthenticationFlowImports(List<AuthenticationFlowImport> authenticationFlowImports) {
        this.authenticationFlowImports = authenticationFlowImports;
    }

    @SuppressWarnings("unused")
    @JsonSetter("userProfile")
    public void setUserProfile(UPConfig userProfile) {
        this.userProfile = userProfile;
    }

    public Map<String, Map<String, String>> getMessageBundles() {
        return messageBundles;
    }

    @SuppressWarnings("unused")
    @JsonSetter("messageBundles")
    public void setMessageBundles(Map<String, Map<String, String>> messageBundles) {
        this.messageBundles = messageBundles;
    }

    public UPConfig getUserProfile() {
        return userProfile;
    }

    @JsonIgnore
    public String getSource() {
        return source;
    }

    @JsonIgnore
    public void setSource(String source) {
        this.source = source;
    }

    public Boolean getAdminPermissionsEnabled() {
        return adminPermissionsEnabled;
    }

    public void setAdminPermissionsEnabled(Boolean adminPermissionsEnabled) {
        this.adminPermissionsEnabled = adminPermissionsEnabled;
    }
}
