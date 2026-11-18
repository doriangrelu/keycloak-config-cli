/*-
 * ---license-start
 * keycloak-config-cli
 * ---
 * Copyright (C) 2017 - 2026 keycloak-config-cli contributors
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

package io.github.doriangrelu.keycloak.config.infrastructure.keycloak.requiredaction;

import io.github.doriangrelu.keycloak.config.application.requiredaction.port.RequiredActionGateway;
import io.github.doriangrelu.keycloak.config.domain.requiredaction.model.RequiredAction;
import io.github.doriangrelu.keycloak.config.infrastructure.keycloak.sdk.api.RequiredActionApi;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@link RequiredActionGateway} on the Keycloak Admin API. */
@Component
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
public class RequiredActionKeycloakGateway implements RequiredActionGateway {

    private final RequiredActionApi requiredActionApi;
    private final RequiredActionKeycloakMapper mapper;

    public RequiredActionKeycloakGateway(
            final RequiredActionApi requiredActionApi, final RequiredActionKeycloakMapper mapper) {
        this.requiredActionApi = requiredActionApi;
        this.mapper = mapper;
    }

    @Override
    public List<RequiredAction> findAll(final String realm) {
        return this.requiredActionApi.listRequiredActions(realm).stream()
                .map(this.mapper::toRequiredAction)
                .toList();
    }

    /**
     * Keycloak only takes the provider and the name at registration, and uses the provider as alias: the other fields,
     * and the alias, are set by an update right after.
     */
    @Override
    public void create(final String realm, final RequiredAction requiredAction) {
        this.requiredActionApi.registerRequiredAction(realm, requiredAction.providerId(), requiredAction.name());
        this.requiredActionApi.updateRequiredAction(
                realm, requiredAction.providerId(), this.mapper.toRepresentation(requiredAction));
    }

    @Override
    public void update(final String realm, final String currentAlias, final RequiredAction requiredAction) {
        this.requiredActionApi.updateRequiredAction(realm, currentAlias, this.mapper.toRepresentation(requiredAction));
    }

    @Override
    public void delete(final String realm, final String alias) {
        this.requiredActionApi.removeRequiredAction(realm, alias);
    }
}
