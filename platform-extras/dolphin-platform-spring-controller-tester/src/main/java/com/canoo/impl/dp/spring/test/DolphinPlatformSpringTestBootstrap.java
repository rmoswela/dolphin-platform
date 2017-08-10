/*
 * Copyright 2015-2017 Canoo Engineering AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.canoo.impl.dp.spring.test;

import com.canoo.dp.impl.platform.core.Assert;
import com.canoo.dp.impl.remoting.BeanManagerImpl;
import com.canoo.dp.impl.server.binding.PropertyBinderImpl;
import com.canoo.dp.impl.server.client.ClientSessionLifecycleHandlerImpl;
import com.canoo.dp.impl.server.context.ClientSessionExecutorImpl;
import com.canoo.dp.impl.server.context.DolphinContext;
import com.canoo.dp.impl.server.context.DolphinContextProvider;
import com.canoo.dp.impl.server.event.DefaultDolphinEventBus;
import com.canoo.dp.impl.server.spring.ClientScopeImpl;
import com.canoo.platform.remoting.BeanManager;
import com.canoo.platform.remoting.client.ClientContext;
import com.canoo.platform.remoting.server.ClientSessionExecutor;
import com.canoo.platform.remoting.server.RemotingContext;
import com.canoo.platform.remoting.server.binding.PropertyBinder;
import com.canoo.platform.remoting.server.event.DolphinEventBus;
import com.canoo.platform.server.client.ClientSession;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.http.HttpSession;
import java.util.concurrent.Executor;

@Configuration
public class DolphinPlatformSpringTestBootstrap {

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    protected TestConfiguration createTestConfiguration(final WebApplicationContext context, final HttpSession httpSession) {
        Assert.requireNonNull(context, "context");
        try {
            return new TestConfiguration(context, httpSession);
        } catch (Exception e) {
            throw new RuntimeException("Can not create test configuration", e);
        }
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    protected ClientContext createClientContext(final TestConfiguration testConfiguration) {
        Assert.requireNonNull(testConfiguration, "testConfiguration");
        return testConfiguration.getClientContext();
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    protected DolphinTestContext createServerContext(final TestConfiguration testConfiguration) {
        Assert.requireNonNull(testConfiguration, "testConfiguration");
        return testConfiguration.getDolphinTestContext();
    }

    /**
     * Method to create a spring managed {@link BeanManagerImpl} instance in client scope.
     *
     * @return the instance
     */
    @Bean(name = "beanManager")
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    protected BeanManager createManager(final TestConfiguration testConfiguration) {
        Assert.requireNonNull(testConfiguration, "testConfiguration");
        return testConfiguration.getDolphinTestContext().getBeanManager();
    }

    @Bean(name = "dolphinSession")
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    protected ClientSession createDolphinSession(final TestConfiguration testConfiguration) {
        Assert.requireNonNull(testConfiguration, "testConfiguration");
        return testConfiguration.getDolphinTestContext().getDolphinSession();
    }

    @Bean(name = "remotingContext")
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    protected RemotingContext createRemotingContext(final TestConfiguration testConfiguration, final PropertyBinder propertyBinder, final DolphinEventBus eventBus) {
        Assert.requireNonNull(testConfiguration, "testConfiguration");
        Assert.requireNonNull(propertyBinder, "propertyBinder");
        Assert.requireNonNull(eventBus, "eventBus");
        return new RemotingContext() {
            @Override
            public String getId() {
                return testConfiguration.getDolphinTestContext().getDolphinSession().getId();
            }

            @Override
            public ClientSessionExecutor createSessionExecutor() {
                return new ClientSessionExecutorImpl(new Executor() {
                    @Override
                    public void execute(Runnable command) {
                        testConfiguration.getDolphinTestContext().runLater(command);
                    }
                });
            }

            @Override
            public PropertyBinder getBinder() {
                return propertyBinder;
            }

            @Override
            public BeanManager getBeanManager() {
                return testConfiguration.getDolphinTestContext().getBeanManager();
            }

            @Override
            public DolphinEventBus getEventBus() {
                return eventBus;
            }

            @Override
            public ClientSession getClientSession() {
                return testConfiguration.getDolphinTestContext().getDolphinSession();
            }
        };
    }

    /**
     * Method to create a spring managed {@link DolphinEventBus} instance in singleton scope.
     *
     * @return the instance
     */
    @Bean(name = "dolphinEventBus")
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    protected DolphinEventBus createEventBus(final TestConfiguration testConfiguration) {
        Assert.requireNonNull(testConfiguration, "testConfiguration");

        final DolphinContextProvider contextProvider = new DolphinContextProvider() {
            @Override
            public DolphinContext getContext(ClientSession clientSession) {
                return getCurrentDolphinContext();
            }

            @Override
            public DolphinContext getContextById(String clientSessionId) {
                return getCurrentDolphinContext();
            }

            @Override
            public DolphinContext getCurrentDolphinContext() {
                return testConfiguration.getDolphinTestContext();
            }
        };

        DefaultDolphinEventBus eventBus = new DefaultDolphinEventBus();
        eventBus.init(contextProvider, new ClientSessionLifecycleHandlerImpl());
        return eventBus;
    }

    @Bean(name = "propertyBinder")
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    protected PropertyBinder createPropertyBinder() {
        return new PropertyBinderImpl();
    }

    @Bean(name = "customScopeConfigurer")
    public static CustomScopeConfigurer createClientScope(final ClientSession clientSession) {
        Assert.requireNonNull(clientSession, "clientSession");
        CustomScopeConfigurer configurer = new CustomScopeConfigurer();
        configurer.addScope(ClientScopeImpl.CLIENT_SCOPE, new TestClientScope(clientSession));
        return configurer;
    }
}
