/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.quarkus.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.TypeConverter;
import org.apache.camel.catalog.RuntimeCamelCatalog;
import org.apache.camel.catalog.impl.DefaultRuntimeCamelCatalog;
import org.apache.camel.component.microprofile.config.CamelMicroProfilePropertiesSource;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.impl.DefaultExecutorServiceManager;
import org.apache.camel.impl.engine.AbstractCamelContext;
import org.apache.camel.impl.engine.BaseServiceResolver;
import org.apache.camel.impl.engine.DefaultAsyncProcessorAwaitManager;
import org.apache.camel.impl.engine.DefaultBeanIntrospection;
import org.apache.camel.impl.engine.DefaultCamelBeanPostProcessor;
import org.apache.camel.impl.engine.DefaultCamelContextNameStrategy;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.impl.engine.DefaultComponentNameResolver;
import org.apache.camel.impl.engine.DefaultComponentResolver;
import org.apache.camel.impl.engine.DefaultConfigurerResolver;
import org.apache.camel.impl.engine.DefaultDataFormatResolver;
import org.apache.camel.impl.engine.DefaultEndpointRegistry;
import org.apache.camel.impl.engine.DefaultHeadersMapFactory;
import org.apache.camel.impl.engine.DefaultInflightRepository;
import org.apache.camel.impl.engine.DefaultInjector;
import org.apache.camel.impl.engine.DefaultLanguageResolver;
import org.apache.camel.impl.engine.DefaultMessageHistoryFactory;
import org.apache.camel.impl.engine.DefaultNodeIdFactory;
import org.apache.camel.impl.engine.DefaultPackageScanClassResolver;
import org.apache.camel.impl.engine.DefaultPackageScanResourceResolver;
import org.apache.camel.impl.engine.DefaultProcessorFactory;
import org.apache.camel.impl.engine.DefaultReactiveExecutor;
import org.apache.camel.impl.engine.DefaultRouteController;
import org.apache.camel.impl.engine.DefaultStreamCachingStrategy;
import org.apache.camel.impl.engine.DefaultTracer;
import org.apache.camel.impl.engine.DefaultTransformerRegistry;
import org.apache.camel.impl.engine.DefaultUnitOfWorkFactory;
import org.apache.camel.impl.engine.DefaultValidatorRegistry;
import org.apache.camel.impl.engine.EndpointKey;
import org.apache.camel.impl.engine.RouteService;
import org.apache.camel.impl.health.DefaultHealthCheckRegistry;
import org.apache.camel.impl.transformer.TransformerKey;
import org.apache.camel.impl.validator.ValidatorKey;
import org.apache.camel.model.Model;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteDefinitionHelper;
import org.apache.camel.processor.MulticastProcessor;
import org.apache.camel.reifier.RouteReifier;
import org.apache.camel.reifier.errorhandler.ErrorHandlerReifier;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.BeanProcessorFactory;
import org.apache.camel.spi.BeanProxyFactory;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.CamelContextNameStrategy;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.ComponentNameResolver;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.ConfigurerResolver;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.spi.EndpointRegistry;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.HeadersMapFactory;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.spi.ManagementNameStrategy;
import org.apache.camel.spi.MessageHistoryFactory;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.spi.ModelToXMLDumper;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.PackageScanResourceResolver;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.RestRegistryFactory;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.spi.Tracer;
import org.apache.camel.spi.TransformerRegistry;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.spi.ValidatorRegistry;
import org.apache.camel.spi.XMLRoutesDefinitionLoader;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FastCamelContext extends AbstractCamelContext {

    private static final Logger LOG = LoggerFactory.getLogger(FastCamelContext.class);

    private final CamelContext reference;
    private final String version;
    private XMLRoutesDefinitionLoader xmlLoader;
    private ModelToXMLDumper modelDumper;
    private Model model;
    private Date startDate;

    public FastCamelContext(CamelContext reference, FactoryFinderResolver factoryFinderResolver, String version,
            XMLRoutesDefinitionLoader xmlLoader,
            ModelToXMLDumper modelDumper) {
        super(false);
        this.reference = reference != null ? reference : this;
        this.version = version;
        this.xmlLoader = xmlLoader;
        this.modelDumper = modelDumper;

        this.model = new FastModel(getCamelContextReference());

        setFactoryFinderResolver(factoryFinderResolver);
        setTracing(Boolean.FALSE);
        setDebugging(Boolean.FALSE);
        setMessageHistory(Boolean.FALSE);
        setDefaultExtension(HealthCheckRegistry.class, DefaultHealthCheckRegistry::new);

        LOG.info("Creating Apache Camel {} (CamelContext: {})", getVersion(), getName());
    }

    @Override
    public CamelContext getCamelContextReference() {
        return reference;
    }

    @Override
    public long getUptimeMillis() {
        if (startDate == null) {
            return 0;
        }
        return new Date().getTime() - startDate.getTime();
    }

    @Override
    public Date getStartDate() {
        return startDate;
    }

    @Override
    public void startRouteDefinitions() throws Exception {
        List<RouteDefinition> routeDefinitions = model.getRouteDefinitions();
        if (routeDefinitions != null) {
            startRouteDefinitions(routeDefinitions);
        }
    }

    public void startRouteDefinitions(List<RouteDefinition> routeDefinitions) throws Exception {
        RouteDefinitionHelper.forceAssignIds(getCamelContextReference(), routeDefinitions);
        for (RouteDefinition routeDefinition : routeDefinitions) {
            // assign ids to the routes and validate that the id's is all unique
            String duplicate = RouteDefinitionHelper.validateUniqueIds(routeDefinition, routeDefinitions);
            if (duplicate != null) {
                throw new FailedToStartRouteException(routeDefinition.getId(),
                        "duplicate id detected: " + duplicate + ". Please correct ids to be unique among all your routes.");
            }

            // must ensure route is prepared, before we can start it
            if (!routeDefinition.isPrepared()) {
                RouteDefinitionHelper.prepareRoute(getCamelContextReference(), routeDefinition);
                routeDefinition.markPrepared();
            }

            // indicate we are staring the route using this thread so
            // we are able to query this if needed
            Route route = new RouteReifier(getCamelContextReference(), routeDefinition).createRoute();
            RouteService routeService = new RouteService(route);
            startRouteService(routeService, true);
        }
    }

    @Override
    public <T> T getExtension(Class<T> type) {
        if (type.isInstance(model)) {
            return type.cast(model);
        }
        return super.getExtension(type);
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    protected Registry createRegistry() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ManagementNameStrategy createManagementNameStrategy() {
        return null;
    }

    @Override
    protected ShutdownStrategy createShutdownStrategy() {
        return new NoShutdownStrategy();
    }

    @Override
    protected UuidGenerator createUuidGenerator() {
        return new FastUuidGenerator();
    }

    @Override
    protected ComponentResolver createComponentResolver() {
        // components are automatically discovered by build steps so we can reduce the
        // operations done by the standard resolver by looking them up directly from the
        // registry
        return (name, context) -> context.getRegistry().lookupByNameAndType(name, Component.class);
    }

    @Override
    protected LanguageResolver createLanguageResolver() {
        return new DefaultLanguageResolver();
    }

    @Override
    protected DataFormatResolver createDataFormatResolver() {
        return new DefaultDataFormatResolver();
    }

    @Override
    protected TypeConverter createTypeConverter() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected TypeConverterRegistry createTypeConverterRegistry() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected HealthCheckRegistry createHealthCheckRegistry() {
        return new DefaultHealthCheckRegistry(getCamelContextReference());
    }

    @Override
    protected Injector createInjector() {
        return new DefaultInjector(getCamelContextReference());
    }

    @Override
    protected CamelBeanPostProcessor createBeanPostProcessor() {
        return new DefaultCamelBeanPostProcessor(getCamelContextReference());
    }

    @Override
    protected ModelJAXBContextFactory createModelJAXBContextFactory() {
        return new DisabledModelJAXBContextFactory();
    }

    @Override
    protected NodeIdFactory createNodeIdFactory() {
        return new DefaultNodeIdFactory();
    }

    @Override
    protected FactoryFinderResolver createFactoryFinderResolver() {
        throw new UnsupportedOperationException(
                "FactoryFinderResolver should have been set in the FastCamelContext constructor");
    }

    @Override
    protected ClassResolver createClassResolver() {
        return new DefaultClassResolver(getCamelContextReference());
    }

    @Override
    protected ProcessorFactory createProcessorFactory() {
        return new DefaultProcessorFactory();
    }

    @Override
    protected MessageHistoryFactory createMessageHistoryFactory() {
        return new DefaultMessageHistoryFactory();
    }

    @Override
    protected InflightRepository createInflightRepository() {
        return new DefaultInflightRepository();
    }

    @Override
    protected AsyncProcessorAwaitManager createAsyncProcessorAwaitManager() {
        return new DefaultAsyncProcessorAwaitManager();
    }

    @Override
    protected RouteController createRouteController() {
        return new DefaultRouteController(getCamelContextReference());
    }

    @Override
    protected PackageScanClassResolver createPackageScanClassResolver() {
        return new DefaultPackageScanClassResolver();
    }

    @Override
    protected ExecutorServiceManager createExecutorServiceManager() {
        return new DefaultExecutorServiceManager(getCamelContextReference());
    }

    @Override
    protected UnitOfWorkFactory createUnitOfWorkFactory() {
        return new DefaultUnitOfWorkFactory();
    }

    protected CamelContextNameStrategy createCamelContextNameStrategy() {
        return new DefaultCamelContextNameStrategy();
    }

    @Override
    protected HeadersMapFactory createHeadersMapFactory() {
        return new BaseServiceResolver<>(HeadersMapFactory.FACTORY, HeadersMapFactory.class)
                .resolve(getCamelContextReference())
                .orElseGet(DefaultHeadersMapFactory::new);
    }

    @Override
    protected BeanProxyFactory createBeanProxyFactory() {
        return new BaseServiceResolver<>(BeanProxyFactory.FACTORY, BeanProxyFactory.class)
                .resolve(getCamelContextReference())
                .orElseThrow(() -> new IllegalArgumentException("Cannot find BeanProxyFactory on classpath. "
                        + "Add camel-bean to classpath."));
    }

    @Override
    protected BeanProcessorFactory createBeanProcessorFactory() {
        return new BaseServiceResolver<>(BeanProcessorFactory.FACTORY, BeanProcessorFactory.class)
                .resolve(getCamelContextReference())
                .orElseThrow(() -> new IllegalArgumentException("Cannot find BeanProcessorFactory on classpath. "
                        + "Add camel-bean to classpath."));
    }

    @Override
    protected BeanIntrospection createBeanIntrospection() {
        return new DefaultBeanIntrospection();
    }

    @Override
    protected PropertiesComponent createPropertiesComponent() {
        org.apache.camel.component.properties.PropertiesComponent pc = new org.apache.camel.component.properties.PropertiesComponent();
        pc.setAutoDiscoverPropertiesSources(false);
        pc.addPropertiesSource(new CamelMicroProfilePropertiesSource());
        return pc;
    }

    @Override
    protected PackageScanResourceResolver createPackageScanResourceResolver() {
        return new DefaultPackageScanResourceResolver();
    }

    @Override
    protected XMLRoutesDefinitionLoader createXMLRoutesDefinitionLoader() {
        return xmlLoader;
    }

    @Override
    protected ModelToXMLDumper createModelToXMLDumper() {
        return modelDumper;
    }

    @Override
    protected RuntimeCamelCatalog createRuntimeCamelCatalog() {
        return new DefaultRuntimeCamelCatalog();
    }

    @Override
    protected Tracer createTracer() {
        Tracer tracer = null;
        if (getRegistry() != null) {
            Map<String, Tracer> map = getRegistry().findByTypeWithName(Tracer.class);
            if (map.size() == 1) {
                tracer = map.values().iterator().next();
            }
        }

        if (tracer == null) {
            tracer = getExtension(Tracer.class);
        }

        if (tracer == null) {
            tracer = new DefaultTracer();
            setExtension(Tracer.class, tracer);
        }

        return tracer;
    }

    @Override
    protected RestRegistryFactory createRestRegistryFactory() {
        return new BaseServiceResolver<>(RestRegistryFactory.FACTORY, RestRegistryFactory.class)
                .resolve(getCamelContextReference())
                .orElseThrow(() -> new IllegalArgumentException("Cannot find RestRegistryFactory on classpath. "
                        + "Add camel-rest to classpath."));
    }

    @Override
    protected EndpointRegistry<EndpointKey> createEndpointRegistry(Map<EndpointKey, Endpoint> endpoints) {
        return new DefaultEndpointRegistry(getCamelContextReference(), endpoints);
    }

    @Override
    protected StreamCachingStrategy createStreamCachingStrategy() {
        return new DefaultStreamCachingStrategy();
    }

    @Override
    protected TransformerRegistry<TransformerKey> createTransformerRegistry() {
        return new DefaultTransformerRegistry(getCamelContextReference());
    }

    @Override
    protected ValidatorRegistry<ValidatorKey> createValidatorRegistry() {
        return new DefaultValidatorRegistry(getCamelContextReference());
    }

    @Override
    protected ReactiveExecutor createReactiveExecutor() {
        return new DefaultReactiveExecutor();
    }

    @Override
    public AsyncProcessor createMulticast(Collection<Processor> processors, ExecutorService executor,
            boolean shutdownExecutorService) {
        return new MulticastProcessor(getCamelContextReference(), processors, null, true, executor, shutdownExecutorService,
                false, false, 0L, null, false, false);
    }

    @Override
    protected ConfigurerResolver createConfigurerResolver() {
        return new DefaultConfigurerResolver();
    }

    @Override
    protected ComponentNameResolver createComponentNameResolver() {
        return new DefaultComponentNameResolver();
    }

    @Override
    public Processor createErrorHandler(Route route, Processor processor) throws Exception {
        return ErrorHandlerReifier.reifier(route, route.getErrorHandlerFactory())
                .createErrorHandler(processor);
    }

    @Override
    public void setTypeConverterRegistry(TypeConverterRegistry typeConverterRegistry) {
        super.setTypeConverterRegistry(typeConverterRegistry);
        typeConverterRegistry.setCamelContext(getCamelContextReference());
    }

    @Override
    public void doInit() throws Exception {
        LOG.info("Apache Camel {} (CamelContext: {}) is initializing", getVersion(), getName());
        super.doInit();

        forceLazyInitialization();
    }

    @Override
    public String getComponentParameterJsonSchema(String componentName) throws IOException {
        Class<?> clazz;

        Object instance = getRegistry().lookupByNameAndType(componentName, Component.class);
        if (instance != null) {
            clazz = instance.getClass();
        } else {
            clazz = getFactoryFinder(DefaultComponentResolver.RESOURCE_PATH).findClass(componentName).orElse(null);
            if (clazz == null) {
                instance = hasComponent(componentName);
                if (instance != null) {
                    clazz = instance.getClass();
                } else {
                    return null;
                }
            }
        }

        // special for ActiveMQ as it is really just JMS
        if ("ActiveMQComponent".equals(clazz.getSimpleName())) {
            return getComponentParameterJsonSchema("jms");
        } else {
            return getJsonSchema(clazz.getPackage().getName(), componentName);
        }
    }

    @Override
    public String getDataFormatParameterJsonSchema(String dataFormatName) throws IOException {
        Class<?> clazz;

        Object instance = getRegistry().lookupByNameAndType(dataFormatName, DataFormat.class);
        if (instance != null) {
            clazz = instance.getClass();
        } else {
            clazz = getFactoryFinder(DefaultDataFormatResolver.DATAFORMAT_RESOURCE_PATH).findClass(dataFormatName).orElse(null);
            if (clazz == null) {
                return null;
            }
        }

        return getJsonSchema(clazz.getPackage().getName(), dataFormatName);
    }

    @Override
    public String getLanguageParameterJsonSchema(String languageName) throws IOException {
        Class<?> clazz;

        Object instance = getRegistry().lookupByNameAndType(languageName, Language.class);
        if (instance != null) {
            clazz = instance.getClass();
        } else {
            clazz = getFactoryFinder(DefaultLanguageResolver.LANGUAGE_RESOURCE_PATH).findClass(languageName).orElse(null);
            if (clazz == null) {
                return null;
            }
        }

        return getJsonSchema(clazz.getPackage().getName(), languageName);
    }

    @Override
    public String getEipParameterJsonSchema(String eipName) throws IOException {
        // the eip json schema may be in some of the sub-packages so look until
        // we find it
        String[] subPackages = new String[] { "", "/config", "/dataformat", "/language", "/loadbalancer", "/rest" };
        for (String sub : subPackages) {
            String path = CamelContextHelper.MODEL_DOCUMENTATION_PREFIX + sub + "/" + eipName + ".json";
            InputStream inputStream = getClassResolver().loadResourceAsStream(path);
            if (inputStream != null) {
                try {
                    return IOHelper.loadText(inputStream);
                } finally {
                    IOHelper.close(inputStream);
                }
            }
        }
        return null;
    }

    private String getJsonSchema(String packageName, String name) throws IOException {
        String path = packageName.replace('.', '/') + "/" + name + ".json";
        InputStream inputStream = getClassResolver().loadResourceAsStream(path);

        if (inputStream != null) {
            try {
                return IOHelper.loadText(inputStream);
            } finally {
                IOHelper.close(inputStream);
            }
        }

        return null;
    }

}
