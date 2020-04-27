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
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.camel.*;
import org.apache.camel.catalog.RuntimeCamelCatalog;
import org.apache.camel.impl.converter.CoreTypeConverterRegistry;
import org.apache.camel.impl.engine.*;
import org.apache.camel.spi.*;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.NormalizedUri;
import org.apache.camel.support.ProcessorEndpoint;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.*;
import org.apache.camel.util.function.ThrowingConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuarkusLightweightRuntimeCamelContext implements ExtendedCamelContext, CatalogCamelContext {

    private static final Logger LOG = LoggerFactory.getLogger(QuarkusLightweightRuntimeCamelContext.class);

    private final AtomicInteger endpointKeyCounter = new AtomicInteger();
    private final GlobalEndpointConfiguration globalEndpointConfiguration = new DefaultGlobalEndpointConfiguration();
    private final CamelContext reference;
    private final Registry registry;
    private final RestConfiguration restConfiguration;
    private final CoreTypeConverterRegistry typeConverter;
    private final ModelJAXBContextFactory modelJAXBContextFactory;
    private final RuntimeCamelCatalog camelRuntimeCatalog;
    private final ComponentResolver componentResolver;
    private final ComponentNameResolver componentNameResolver;
    private final LanguageResolver languageResolver;
    private final DataFormatResolver dataFormatResolver;
    private final UuidGenerator uuidGenerator;
    private final EndpointRegistry<EndpointKey> endpoints;
    private final Map<String, Component> components;
    private final Map<String, Language> languages;
    private final PropertiesComponent propertiesComponent;
    private final BeanIntrospection beanIntrospection;
    private final HeadersMapFactory headersMapFactory;
    private final ReactiveExecutor reactiveExecutor;
    private final AsyncProcessorAwaitManager asyncProcessorAwaitManager;
    private final ExecutorServiceManager executorServiceManager;
    private final ShutdownStrategy shutdownStrategy;
    private final ClassLoader applicationContextClassLoader;
    private final UnitOfWorkFactory unitOfWorkFactory;
    private final FactoryFinderResolver factoryFinderResolver;
    private final RouteController routeController;
    private final InflightRepository inflightRepository;
    private final Injector injector;
    private final ClassResolver classResolver;
    private final ConfigurerResolver configurerResolver;
    private final Map<String, String> globalOptions;
    private final String name;
    private final boolean eventNotificationApplicable;
    private final boolean useDataType;
    private final boolean useBreadcrumb;
    private final String mdcLoggingKeysPattern;
    private final boolean useMDCLogging;
    private final List<Route> routes;
    private final boolean messageHistory;
    private final boolean logMask;
    private final boolean allowUseOriginalMessage;
    private final boolean logExhaustedMessageBody;
    private final String managementName;
    private final String version;
    private final StreamCachingStrategy streamCachingStrategy;
    private final boolean streamCaching;
    private final List<StartupListener> startupListeners;
    private Date startDate;

    QuarkusLightweightRuntimeCamelContext(CamelContext reference, CamelContext context) {
        this.reference = reference;
        registry = context.getRegistry();
        restConfiguration = context.getRestConfiguration();
        typeConverter = new CoreTypeConverterRegistry(context.getTypeConverterRegistry());
        modelJAXBContextFactory = context.adapt(ExtendedCamelContext.class).getModelJAXBContextFactory();
        camelRuntimeCatalog = context.adapt(ExtendedCamelContext.class).getRuntimeCamelCatalog();
        routes = Collections.unmodifiableList(context.getRoutes());
        uuidGenerator = context.getUuidGenerator();
        componentResolver = context.adapt(ExtendedCamelContext.class).getComponentResolver();
        componentNameResolver = context.adapt(ExtendedCamelContext.class).getComponentNameResolver();
        languageResolver = context.adapt(ExtendedCamelContext.class).getLanguageResolver();
        dataFormatResolver = context.adapt(ExtendedCamelContext.class).getDataFormatResolver();
        endpoints = (EndpointRegistry) context.getEndpointRegistry();
        components = context.getComponentNames().stream()
                .collect(Collectors.toMap(s -> s, s -> context.hasComponent(s)));
        languages = context.getLanguageNames().stream()
                .collect(Collectors.toMap(s -> s, s -> context.resolveLanguage(s)));
        propertiesComponent = context.getPropertiesComponent();
        beanIntrospection = context.adapt(ExtendedCamelContext.class).getBeanIntrospection();
        headersMapFactory = context.adapt(ExtendedCamelContext.class).getHeadersMapFactory();
        reactiveExecutor = context.adapt(ExtendedCamelContext.class).getReactiveExecutor();
        asyncProcessorAwaitManager = context.adapt(ExtendedCamelContext.class).getAsyncProcessorAwaitManager();
        executorServiceManager = context.getExecutorServiceManager();
        shutdownStrategy = context.getShutdownStrategy();
        applicationContextClassLoader = context.getApplicationContextClassLoader();
        unitOfWorkFactory = context.adapt(ExtendedCamelContext.class).getUnitOfWorkFactory();
        factoryFinderResolver = context.adapt(ExtendedCamelContext.class).getFactoryFinderResolver();
        routeController = context.getRouteController();
        inflightRepository = context.getInflightRepository();
        globalOptions = context.getGlobalOptions();
        injector = context.getInjector();
        classResolver = context.getClassResolver();
        configurerResolver = context.adapt(ExtendedCamelContext.class).getConfigurerResolver();
        name = context.getName();
        eventNotificationApplicable = context.adapt(ExtendedCamelContext.class).isEventNotificationApplicable();
        useDataType = context.isUseDataType();
        useBreadcrumb = context.isUseBreadcrumb();
        mdcLoggingKeysPattern = context.getMDCLoggingKeysPattern();
        useMDCLogging = context.isUseMDCLogging();
        messageHistory = context.isMessageHistory();
        logMask = context.isLogMask();
        allowUseOriginalMessage = context.isAllowUseOriginalMessage();
        logExhaustedMessageBody = context.isLogExhaustedMessageBody();
        managementName = context.getManagementName();
        version = context.getVersion();
        streamCachingStrategy = context.getStreamCachingStrategy();
        streamCaching = context.isStreamCaching();
        startupListeners = ((FastCamelContext) context).getStartupListeners();
    }

    public CamelContext getCamelContextReference() {
        return reference;
    }

    //
    // Lifecycle
    //

    @Override
    public boolean isStarted() {
        return false;
    }

    @Override
    public boolean isStarting() {
        return false;
    }

    @Override
    public boolean isStopped() {
        return false;
    }

    @Override
    public boolean isStopping() {
        return false;
    }

    @Override
    public boolean isSuspended() {
        return false;
    }

    @Override
    public boolean isRunAllowed() {
        return false;
    }

    @Override
    public boolean isSuspending() {
        return false;
    }

    @Override
    public void build() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void init() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void suspend() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void resume() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void start() {
        startDate = new Date();
        LOG.info("Apache Camel {} (CamelContext: {}) is starting", getVersion(), getName());
        foreach(startupListeners, s -> s.onCamelContextStarting(getCamelContextReference(), false));
        foreach(components.values(), this::startService);
        foreach(routes, this::startService);
        foreach(startupListeners, s -> s.onCamelContextStarted(getCamelContextReference(), false));
        for (Route route : routes) {
            route.getConsumer().start();
        }
        foreach(startupListeners, s -> s.onCamelContextFullyStarted(getCamelContextReference(), false));
        if (LOG.isInfoEnabled()) {
            long l = System.currentTimeMillis() - startDate.getTime();
            LOG.info("Apache Camel {} (CamelContext: {}) {} routes started in {}",
                    getVersion(), getName(), routes.size(), TimeUtils.printDuration(l));
        }
    }

    private <T> void foreach(Iterable<T> list, ThrowingConsumer<T, Exception> runner) {
        for (T element : list) {
            try {
                runner.accept(element);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }
    }

    @Override
    public void stop() {
        for (Route route : routes) {
            route.getConsumer().stop();
        }
    }

    @Override
    public void shutdown() {
    }

    @Override
    public void close() throws IOException {
        stop();
    }

    //
    // RuntimeConfig
    //

    @Override
    public void setStreamCaching(Boolean cache) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isStreamCaching() {
        return streamCaching;
    }

    @Override
    public Boolean isTracing() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getTracingPattern() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isBacklogTracing() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isDebugging() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isMessageHistory() {
        return messageHistory;
    }

    @Override
    public Boolean isLogMask() {
        return logMask;
    }

    @Override
    public Boolean isLogExhaustedMessageBody() {
        return logExhaustedMessageBody;
    }

    @Override
    public Long getDelayer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isAutoStartup() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ShutdownRoute getShutdownRoute() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ShutdownRunningTask getShutdownRunningTask() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAllowUseOriginalMessage(Boolean allowUseOriginalMessage) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isAllowUseOriginalMessage() {
        return allowUseOriginalMessage;
    }

    @Override
    public Boolean isCaseInsensitiveHeaders() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCaseInsensitiveHeaders(Boolean caseInsensitiveHeaders) {
        throw new UnsupportedOperationException();
    }

    //
    // Model
    //

    @Override
    public Registry getRegistry() {
        return registry;
    }

    @Override
    public TypeConverterRegistry getTypeConverterRegistry() {
        return typeConverter;
    }

    @Override
    public ModelJAXBContextFactory getModelJAXBContextFactory() {
        return modelJAXBContextFactory;
    }

    @Override
    public ComponentResolver getComponentResolver() {
        return componentResolver;
    }

    @Override
    public ComponentNameResolver getComponentNameResolver() {
        return componentNameResolver;
    }

    @Override
    public LanguageResolver getLanguageResolver() {
        return languageResolver;
    }

    @Override
    public DataFormatResolver getDataFormatResolver() {
        return dataFormatResolver;
    }

    @Override
    public UuidGenerator getUuidGenerator() {
        return uuidGenerator;
    }

    @Override
    public <T extends CamelContext> T adapt(Class<T> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T getExtension(Class<T> type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> void setExtension(Class<T> type, T module) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isVetoStarted() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public CamelContextNameStrategy getNameStrategy() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setNameStrategy(CamelContextNameStrategy nameStrategy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ManagementNameStrategy getManagementNameStrategy() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setManagementNameStrategy(ManagementNameStrategy nameStrategy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getManagementName() {
        return managementName;
    }

    @Override
    public void setManagementName(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public ServiceStatus getStatus() {
        return ServiceStatus.Started;
    }

    @Override
    public String getUptime() {
        long delta = getUptimeMillis();
        if (delta == 0) {
            return "";
        }
        return TimeUtils.printDuration(delta);
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
    public void addService(Object object) throws Exception {
        addService(object, true);
    }

    @Override
    public void addService(Object object, boolean stopOnShutdown) throws Exception {
        addService(object, stopOnShutdown, false);
    }

    @Override
    public void addService(Object object, boolean stopOnShutdown, boolean forceStart) throws Exception {
        startService(object);
    }

    @Override
    public void addPrototypeService(Object object) throws Exception {
        startService(object);
    }

    @Override
    public boolean removeService(Object object) throws Exception {
        return false;
    }

    @Override
    public boolean hasService(Object object) {
        return false;
    }

    @Override
    public <T> T hasService(Class<T> type) {
        return null;
    }

    @Override
    public <T> Set<T> hasServices(Class<T> type) {
        return null;
    }

    @Override
    public void deferStartService(Object object, boolean stopOnShutdown) throws Exception {
    }

    @Override
    public void addStartupListener(StartupListener listener) throws Exception {
        startupListeners.add(listener);
    }

    @Override
    public Component hasComponent(String name) {
        return components.get(name);
    }

    @Override
    public Component getComponent(String name) {
        return getComponent(name, true, true);
    }

    @Override
    public Component getComponent(String name, boolean autoCreateComponents) {
        return getComponent(name, autoCreateComponents, true);
    }

    @Override
    public Component getComponent(String name, boolean autoCreateComponents, boolean autoStart) {
        Component component = components.get(name);
        if (component == null && autoCreateComponents) {
            try {
                component = getComponentResolver().resolveComponent(name, getCamelContextReference());
                if (component != null) {
                    component.setCamelContext(getCamelContextReference());
                    component.build();
                    if (autoStart) {
                        startService(component);
                    }
                    components.put(name, component);
                }
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }
        return component;
    }

    @Override
    public <T extends Component> T getComponent(String name, Class<T> componentType) {
        return componentType.cast(hasComponent(name));
    }

    @Override
    public List<String> getComponentNames() {
        return new ArrayList<>(components.keySet());
    }

    @Override
    public EndpointRegistry<? extends ValueHolder<String>> getEndpointRegistry() {
        return endpoints;
    }

    @Override
    public Endpoint getEndpoint(String uri) {
        return doGetEndpoint(uri, false, false);
    }

    /**
     * Normalize uri so we can do endpoint hits with minor mistakes and
     * parameters is not in the same order.
     *
     * @param  uri                            the uri
     * @return                                normalized uri
     * @throws ResolveEndpointFailedException if uri cannot be normalized
     */
    protected static String normalizeEndpointUri(String uri) {
        try {
            uri = URISupport.normalizeUri(uri);
        } catch (Exception e) {
            throw new ResolveEndpointFailedException(uri, e);
        }
        return uri;
    }

    @Override
    public Endpoint getEndpoint(String uri, Map<String, Object> parameters) {
        return doGetEndpoint(uri, parameters, false);
    }

    @Override
    public <T extends Endpoint> T getEndpoint(String name, Class<T> endpointType) {
        Endpoint endpoint = getEndpoint(name);
        if (endpoint == null) {
            throw new NoSuchEndpointException(name);
        }
        if (endpoint instanceof InterceptSendToEndpoint) {
            endpoint = ((InterceptSendToEndpoint) endpoint).getOriginalEndpoint();
        }
        if (endpointType.isInstance(endpoint)) {
            return endpointType.cast(endpoint);
        } else {
            throw new IllegalArgumentException(
                    "The endpoint is not of type: " + endpointType + " but is: " + endpoint.getClass().getCanonicalName());
        }
    }

    @Override
    public Collection<Endpoint> getEndpoints() {
        return new ArrayList<>(endpoints.values());
    }

    @Override
    public Map<String, Endpoint> getEndpointMap() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Endpoint hasEndpoint(String uri) {
        return endpoints.get(new EndpointKey(uri));
    }

    @Override
    public GlobalEndpointConfiguration getGlobalEndpointConfiguration() {
        return globalEndpointConfiguration;
    }

    @Override
    public RouteController getRouteController() {
        return routeController;
    }

    @Override
    public List<Route> getRoutes() {
        return routes;
    }

    @Override
    public int getRoutesSize() {
        return routes.size();
    }

    @Override
    public Route getRoute(String id) {
        return routes.stream().filter(r -> r.getId().equals(id)).findFirst().orElse(null);
    }

    @Override
    public Processor getProcessor(String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends Processor> T getProcessor(String id, Class<T> type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<RoutePolicyFactory> getRoutePolicyFactories() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RestConfiguration getRestConfiguration() {
        return restConfiguration;
    }

    @Override
    public RestRegistry getRestRegistry() {
        throw new UnsupportedOperationException();
    }

    @Override
    public TypeConverter getTypeConverter() {
        return typeConverter;
    }

    @Override
    public <T> T getRegistry(Class<T> type) {
        return type.cast(registry);
    }

    @Override
    public Injector getInjector() {
        return injector;
    }

    @Override
    public List<LifecycleStrategy> getLifecycleStrategies() {
        return Collections.emptyList();
    }

    @Override
    public Language resolveLanguage(String language) throws NoSuchLanguageException {
        Language answer;
        synchronized (languages) {
            answer = languages.get(language);
            // check if the language is singleton, if so return the shared
            // instance
            if (answer instanceof IsSingleton) {
                boolean singleton = ((IsSingleton) answer).isSingleton();
                if (singleton) {
                    return answer;
                }
            }
            // language not known or not singleton, then use resolver
            answer = getLanguageResolver().resolveLanguage(language, reference);
            // inject CamelContext if aware
            if (answer != null) {
                try {
                    startService(answer);
                } catch (Exception e) {
                    throw RuntimeCamelException.wrapRuntimeCamelException(e);
                }
                languages.put(language, answer);
            }
        }
        return answer;
    }

    @Override
    public String resolvePropertyPlaceholders(String text) {
        if (text != null && text.contains(PropertiesComponent.PREFIX_TOKEN)) {
            // the parser will throw exception if property key was not found
            return getPropertiesComponent().parseUri(text);
        }
        // is the value a known field (currently we only support
        // constants from Exchange.class)
        if (text != null && text.startsWith("Exchange.")) {
            String field = StringHelper.after(text, "Exchange.");
            String constant = ExchangeConstantProvider.lookup(field);
            if (constant != null) {
                return constant;
            } else {
                throw new IllegalArgumentException("Constant field with name: " + field + " not found on Exchange.class");
            }
        }
        // return original text as is
        return text;
    }

    @Override
    public PropertiesComponent getPropertiesComponent() {
        return propertiesComponent;
    }

    @Override
    public List<String> getLanguageNames() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ProducerTemplate createProducerTemplate() {
        return createProducerTemplate(0);
    }

    @Override
    public ProducerTemplate createProducerTemplate(int maximumCacheSize) {
        DefaultProducerTemplate answer = new DefaultProducerTemplate(getCamelContextReference());
        answer.setMaximumCacheSize(maximumCacheSize);
        answer.start();
        return answer;
    }

    @Override
    public FluentProducerTemplate createFluentProducerTemplate() {
        return createFluentProducerTemplate(0);
    }

    @Override
    public FluentProducerTemplate createFluentProducerTemplate(int maximumCacheSize) {
        DefaultFluentProducerTemplate answer = new DefaultFluentProducerTemplate(getCamelContextReference());
        answer.setMaximumCacheSize(maximumCacheSize);
        answer.start();
        return answer;
    }

    @Override
    public ConsumerTemplate createConsumerTemplate() {
        return createConsumerTemplate(0);
    }

    @Override
    public ConsumerTemplate createConsumerTemplate(int maximumCacheSize) {
        DefaultConsumerTemplate answer = new DefaultConsumerTemplate(getCamelContextReference());
        answer.setMaximumCacheSize(maximumCacheSize);
        answer.start();
        return answer;
    }

    @Override
    public DataFormat resolveDataFormat(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataFormat createDataFormat(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Transformer resolveTransformer(String model) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Transformer resolveTransformer(DataType from, DataType to) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TransformerRegistry getTransformerRegistry() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Validator resolveValidator(DataType type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ValidatorRegistry getValidatorRegistry() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setGlobalOptions(Map<String, String> globalOptions) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> getGlobalOptions() {
        return globalOptions;
    }

    @Override
    public String getGlobalOption(String key) {
        String value = getGlobalOptions().get(key);
        if (ObjectHelper.isNotEmpty(value)) {
            try {
                value = resolvePropertyPlaceholders(value);
            } catch (Exception e) {
                throw new RuntimeCamelException("Error getting global option: " + key, e);
            }
        }
        return value;
    }

    @Override
    public ClassResolver getClassResolver() {
        return classResolver;
    }

    @Override
    public ManagementStrategy getManagementStrategy() {
        // TODO: should be null but the DefaultConsumerCache does not support it yet
        // return null;
        return new ManagementStrategy() {
            @Override
            public void manageObject(Object managedObject) throws Exception {

            }

            @Override
            public void unmanageObject(Object managedObject) throws Exception {

            }

            @Override
            public boolean isManaged(Object managedObject) {
                return false;
            }

            @Override
            public boolean isManagedName(Object name) {
                return false;
            }

            @Override
            public void notify(CamelEvent event) throws Exception {

            }

            @Override
            public List<EventNotifier> getEventNotifiers() {
                return null;
            }

            @Override
            public void addEventNotifier(EventNotifier eventNotifier) {

            }

            @Override
            public boolean removeEventNotifier(EventNotifier eventNotifier) {
                return false;
            }

            @Override
            public EventFactory getEventFactory() {
                return null;
            }

            @Override
            public void setEventFactory(EventFactory eventFactory) {

            }

            @Override
            public ManagementObjectNameStrategy getManagementObjectNameStrategy() {
                return null;
            }

            @Override
            public void setManagementObjectNameStrategy(ManagementObjectNameStrategy strategy) {

            }

            @Override
            public ManagementObjectStrategy getManagementObjectStrategy() {
                return null;
            }

            @Override
            public void setManagementObjectStrategy(ManagementObjectStrategy strategy) {

            }

            @Override
            public ManagementAgent getManagementAgent() {
                return null;
            }

            @Override
            public void setManagementAgent(ManagementAgent managementAgent) {

            }

            @Override
            public boolean manageProcessor(NamedNode definition) {
                return false;
            }

            @Override
            public void start() {

            }

            @Override
            public void stop() {

            }
        };
    }

    @Override
    public void disableJMX() throws IllegalStateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public InflightRepository getInflightRepository() {
        return inflightRepository;
    }

    @Override
    public ClassLoader getApplicationContextClassLoader() {
        return applicationContextClassLoader;
    }

    @Override
    public ShutdownStrategy getShutdownStrategy() {
        return shutdownStrategy;
    }

    @Override
    public ExecutorServiceManager getExecutorServiceManager() {
        return executorServiceManager;
    }

    @Override
    public MessageHistoryFactory getMessageHistoryFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Debugger getDebugger() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Tracer getTracer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isLoadTypeConverters() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setLoadTypeConverters(Boolean loadTypeConverters) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isTypeConverterStatisticsEnabled() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTypeConverterStatisticsEnabled(Boolean typeConverterStatisticsEnabled) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isUseMDCLogging() {
        return useMDCLogging;
    }

    @Override
    public void setUseMDCLogging(Boolean useMDCLogging) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getMDCLoggingKeysPattern() {
        return mdcLoggingKeysPattern;
    }

    @Override
    public void setMDCLoggingKeysPattern(String pattern) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isUseDataType() {
        return useDataType;
    }

    @Override
    public void setUseDataType(Boolean useDataType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isUseBreadcrumb() {
        return useBreadcrumb;
    }

    @Override
    public void setUseBreadcrumb(Boolean useBreadcrumb) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StreamCachingStrategy getStreamCachingStrategy() {
        return streamCachingStrategy;
    }

    @Override
    public RuntimeEndpointRegistry getRuntimeEndpointRegistry() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SSLContextParameters getSSLContextParameters() {
        throw new UnsupportedOperationException();
    }

    //
    // ExtendedCamelContext
    //

    @Override
    public Endpoint getPrototypeEndpoint(String uri) {
        return doGetEndpoint(uri, false, true);
    }

    @Override
    public Endpoint getPrototypeEndpoint(NormalizedEndpointUri uri) {
        return doGetEndpoint(uri.getUri(), true, true);
    }

    @Override
    public Endpoint hasEndpoint(NormalizedEndpointUri uri) {
        EndpointKey key;
        if (uri instanceof EndpointKey) {
            key = (EndpointKey) uri;
        } else {
            key = getEndpointKeyPreNormalized(uri.getUri());
        }
        return endpoints.get(key);
    }

    @Override
    public Endpoint getEndpoint(NormalizedEndpointUri uri) {
        return doGetEndpoint(uri.getUri(), true, false);
    }

    @Override
    public Endpoint getEndpoint(NormalizedEndpointUri uri, Map<String, Object> parameters) {
        return doGetEndpoint(uri.getUri(), parameters, true);
    }

    protected Endpoint doGetEndpoint(String uri, boolean normalized, boolean prototype) {
        StringHelper.notEmpty(uri, "uri");
        // in case path has property placeholders then try to let property
        // component resolve those
        if (!normalized) {
            try {
                uri = resolvePropertyPlaceholders(uri);
            } catch (Exception e) {
                throw new ResolveEndpointFailedException(uri, e);
            }
        }
        final String rawUri = uri;
        // normalize uri so we can do endpoint hits with minor mistakes and
        // parameters is not in the same order
        if (!normalized) {
            uri = normalizeEndpointUri(uri);
        }
        String scheme;
        Endpoint answer = null;
        if (!prototype) {
            // use optimized method to get the endpoint uri
            EndpointKey key = getEndpointKeyPreNormalized(uri);
            // only lookup and reuse existing endpoints if not prototype scoped
            answer = endpoints.get(key);
        }

        if (answer == null) {
            try {
                scheme = StringHelper.before(uri, ":");
                if (scheme == null) {
                    // it may refer to a logical endpoint
                    answer = getRegistry().lookupByNameAndType(uri, Endpoint.class);
                    if (answer != null) {
                        return answer;
                    } else {
                        throw new NoSuchEndpointException(uri);
                    }
                }
                LOG.trace("Endpoint uri: {} is from component with name: {}", uri, scheme);
                Component component = getComponent(scheme);
                ServiceHelper.initService(component);

                // Ask the component to resolve the endpoint.
                if (component != null) {
                    LOG.trace("Creating endpoint from uri: {} using component: {}", uri, component);

                    // Have the component create the endpoint if it can.
                    if (component.useRawUri()) {
                        answer = component.createEndpoint(rawUri);
                    } else {
                        answer = component.createEndpoint(uri);
                    }

                    if (answer != null && LOG.isDebugEnabled()) {
                        LOG.debug("{} converted to endpoint: {} by component: {}", URISupport.sanitizeUri(uri), answer,
                                component);
                    }
                }

                if (answer == null) {
                    // no component then try in registry and elsewhere
                    answer = createEndpoint(uri);
                    LOG.trace("No component to create endpoint from uri: {} fallback lookup in registry -> {}", uri, answer);
                }

                if (answer != null) {
                    if (!prototype) {
                        addService(answer);
                        // register in registry
                        answer = addEndpointToRegistry(uri, answer);
                    } else {
                        addPrototypeService(answer);
                    }
                }
            } catch (NoSuchEndpointException e) {
                // throw as-is
                throw e;
            } catch (Exception e) {
                throw new ResolveEndpointFailedException(uri, e);
            }
        }

        // unknown scheme
        if (answer == null) {
            throw new NoSuchEndpointException(uri);
        }
        return answer;
    }

    protected Endpoint doGetEndpoint(String uri, Map<String, Object> parameters, boolean normalized) {
        StringHelper.notEmpty(uri, "uri");
        // in case path has property placeholders then try to let property
        // component resolve those
        if (!normalized) {
            try {
                uri = resolvePropertyPlaceholders(uri);
            } catch (Exception e) {
                throw new ResolveEndpointFailedException(uri, e);
            }
        }
        final String rawUri = uri;
        // normalize uri so we can do endpoint hits with minor mistakes and
        // parameters is not in the same order
        if (!normalized) {
            uri = normalizeEndpointUri(uri);
        }
        Endpoint answer;
        String scheme = null;
        // use optimized method to get the endpoint uri
        EndpointKey key = getEndpointKeyPreNormalized(uri);
        answer = endpoints.get(key);
        if (answer == null) {
            try {
                scheme = StringHelper.before(uri, ":");
                if (scheme == null) {
                    // it may refer to a logical endpoint
                    answer = getRegistry().lookupByNameAndType(uri, Endpoint.class);
                    if (answer != null) {
                        return answer;
                    } else {
                        throw new NoSuchEndpointException(uri);
                    }
                }
                LOG.trace("Endpoint uri: {} is from component with name: {}", uri, scheme);
                Component component = getComponent(scheme);

                // Ask the component to resolve the endpoint.
                if (component != null) {
                    LOG.trace("Creating endpoint from uri: {} using component: {}", uri, component);

                    // Have the component create the endpoint if it can.
                    if (component.useRawUri()) {
                        answer = component.createEndpoint(rawUri, parameters);
                    } else {
                        answer = component.createEndpoint(uri, parameters);
                    }

                    if (answer != null && LOG.isDebugEnabled()) {
                        LOG.debug("{} converted to endpoint: {} by component: {}", URISupport.sanitizeUri(uri), answer,
                                component);
                    }
                }

                if (answer == null) {
                    // no component then try in registry and elsewhere
                    answer = createEndpoint(uri);
                    LOG.trace("No component to create endpoint from uri: {} fallback lookup in registry -> {}", uri, answer);
                }

                if (answer != null) {
                    addService(answer);
                    answer = addEndpointToRegistry(uri, answer);
                }
            } catch (Exception e) {
                throw new ResolveEndpointFailedException(uri, e);
            }
        }

        // unknown scheme
        if (answer == null) {
            throw new ResolveEndpointFailedException(uri, "No component found with scheme: " + scheme);
        }
        return answer;
    }

    protected Endpoint addEndpointToRegistry(String uri, Endpoint endpoint) {
        StringHelper.notEmpty(uri, "uri");
        ObjectHelper.notNull(endpoint, "endpoint");
        endpoints.put(getEndpointKey(uri, endpoint), endpoint);
        return endpoint;
    }

    protected Endpoint createEndpoint(String uri) {
        Object value = getRegistry().lookupByName(uri);
        if (value instanceof Endpoint) {
            return (Endpoint) value;
        } else if (value instanceof Processor) {
            return new ProcessorEndpoint(uri, getCamelContextReference(), (Processor) value);
        }
        return null;
    }

    protected EndpointKey getEndpointKey(String uri, Endpoint endpoint) {
        if (endpoint != null && !endpoint.isSingleton()) {
            int counter = endpointKeyCounter.incrementAndGet();
            return new EndpointKey(uri + ":" + counter);
        } else {
            return new EndpointKey(uri);
        }
    }

    protected EndpointKey getEndpointKeyPreNormalized(String uri) {
        return new EndpointKey(uri, true);
    }

    @Override
    public NormalizedEndpointUri normalizeUri(String uri) {
        try {
            uri = resolvePropertyPlaceholders(uri);
            uri = normalizeEndpointUri(uri);
            return new NormalizedUri(uri);
        } catch (Exception e) {
            throw new ResolveEndpointFailedException(uri, e);
        }
    }

    @Override
    public List<RouteStartupOrder> getRouteStartupOrder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public CamelBeanPostProcessor getBeanPostProcessor() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ManagementMBeanAssembler getManagementMBeanAssembler() {
        throw new UnsupportedOperationException();
    }

    @Override
    public AsyncProcessor createMulticast(Collection<Processor> processors, ExecutorService executor,
            boolean shutdownExecutorService) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ErrorHandlerFactory getErrorHandlerFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public PackageScanClassResolver getPackageScanClassResolver() {
        throw new UnsupportedOperationException();
    }

    @Override
    public PackageScanResourceResolver getPackageScanResourceResolver() {
        throw new UnsupportedOperationException();
    }

    @Override
    public FactoryFinder getDefaultFactoryFinder() {
        return getFactoryFinder(FactoryFinder.DEFAULT_PATH);
    }

    @Override
    public FactoryFinder getFactoryFinder(String path) {
        return factoryFinderResolver.resolveFactoryFinder(getClassResolver(), path);
    }

    @Override
    public FactoryFinderResolver getFactoryFinderResolver() {
        return factoryFinderResolver;
    }

    @Override
    public ProcessorFactory getProcessorFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeferServiceFactory getDeferServiceFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public UnitOfWorkFactory getUnitOfWorkFactory() {
        return unitOfWorkFactory;
    }

    @Override
    public AnnotationBasedProcessorFactory getAnnotationBasedProcessorFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public BeanProxyFactory getBeanProxyFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public BeanProcessorFactory getBeanProcessorFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledExecutorService getErrorHandlerExecutorService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<InterceptStrategy> getInterceptStrategies() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<LogListener> getLogListeners() {
        return Collections.emptySet();
    }

    @Override
    public AsyncProcessorAwaitManager getAsyncProcessorAwaitManager() {
        return asyncProcessorAwaitManager;
    }

    @Override
    public BeanIntrospection getBeanIntrospection() {
        return beanIntrospection;
    }

    @Override
    public HeadersMapFactory getHeadersMapFactory() {
        return headersMapFactory;
    }

    @Override
    public ReactiveExecutor getReactiveExecutor() {
        return reactiveExecutor;
    }

    @Override
    public boolean isEventNotificationApplicable() {
        return eventNotificationApplicable;
    }

    @Override
    public ModelToXMLDumper getModelToXMLDumper() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RuntimeCamelCatalog getRuntimeCamelCatalog() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ConfigurerResolver getConfigurerResolver() {
        return configurerResolver;
    }

    @Override
    public void addRoute(Route route) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeRoute(Route route) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Processor createErrorHandler(Route route, Processor processor) throws Exception {
        // TODO: need to revisit this in order to support dynamic endpoints uri
        throw new UnsupportedOperationException();
    }

    //
    // CatalogCamelContext
    //

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

    //
    // Unsupported mutable methods
    //

    @Override
    public void setName(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRegistry(Registry registry) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setupRoutes(boolean done) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSetupRoutes() {
        return false;
    }

    @Override
    public void setStreamCachingStrategy(StreamCachingStrategy streamCachingStrategy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setErrorHandlerFactory(ErrorHandlerFactory errorHandlerFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setComponentResolver(ComponentResolver componentResolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setComponentNameResolver(ComponentNameResolver componentResolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setLanguageResolver(LanguageResolver languageResolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setDataFormatResolver(DataFormatResolver dataFormatResolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPackageScanClassResolver(PackageScanClassResolver resolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPackageScanResourceResolver(PackageScanResourceResolver resolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setFactoryFinderResolver(FactoryFinderResolver resolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setProcessorFactory(ProcessorFactory processorFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setModelJAXBContextFactory(ModelJAXBContextFactory modelJAXBContextFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setUnitOfWorkFactory(UnitOfWorkFactory unitOfWorkFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addInterceptStrategy(InterceptStrategy interceptStrategy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setupManagement(Map<String, Object> options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addLogListener(LogListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAsyncProcessorAwaitManager(AsyncProcessorAwaitManager manager) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBeanIntrospection(BeanIntrospection beanIntrospection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setHeadersMapFactory(HeadersMapFactory factory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setReactiveExecutor(ReactiveExecutor reactiveExecutor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setEventNotificationApplicable(boolean eventNotificationApplicable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setXMLRoutesDefinitionLoader(XMLRoutesDefinitionLoader xmlRoutesDefinitionLoader) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setModelToXMLDumper(ModelToXMLDumper modelToXMLDumper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRestBindingJaxbDataFormatFactory(RestBindingJaxbDataFormatFactory restBindingJaxbDataFormatFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RestBindingJaxbDataFormatFactory getRestBindingJaxbDataFormatFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRuntimeCamelCatalog(RuntimeCamelCatalog runtimeCamelCatalog) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setConfigurerResolver(ConfigurerResolver configurerResolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public XMLRoutesDefinitionLoader getXMLRoutesDefinitionLoader() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void registerEndpointCallback(EndpointStrategy strategy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setNodeIdFactory(NodeIdFactory factory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Endpoint addEndpoint(String uri, Endpoint endpoint) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeEndpoint(Endpoint endpoint) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<Endpoint> removeEndpoints(String pattern) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public NodeIdFactory getNodeIdFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRuntimeEndpointRegistry(RuntimeEndpointRegistry runtimeEndpointRegistry) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setSSLContextParameters(SSLContextParameters sslContextParameters) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTracer(Tracer tracer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setUuidGenerator(UuidGenerator uuidGenerator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addComponent(String componentName, Component component) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Component removeComponent(String componentName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRouteController(RouteController routeController) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addRoutes(RoutesBuilder builder) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeRoute(String routeId) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addRoutePolicyFactory(RoutePolicyFactory routePolicyFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRestConfiguration(RestConfiguration restConfiguration) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRestRegistry(RestRegistry restRegistry) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setDebugger(Debugger debugger) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTracing(Boolean tracing) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTracingPattern(String tracePattern) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBacklogTracing(Boolean backlogTrace) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setDebugging(Boolean debugging) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setMessageHistory(Boolean messageHistory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setLogMask(Boolean logMask) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setLogExhaustedMessageBody(Boolean logExhaustedMessageBody) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setDelayer(Long delay) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAutoStartup(Boolean autoStartup) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setShutdownRoute(ShutdownRoute shutdownRoute) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setShutdownRunningTask(ShutdownRunningTask shutdownRunningTask) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setClassResolver(ClassResolver resolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setManagementStrategy(ManagementStrategy strategy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setInflightRepository(InflightRepository repository) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setApplicationContextClassLoader(ClassLoader classLoader) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setShutdownStrategy(ShutdownStrategy shutdownStrategy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setExecutorServiceManager(ExecutorServiceManager executorServiceManager) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setMessageHistoryFactory(MessageHistoryFactory messageHistoryFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTypeConverterRegistry(TypeConverterRegistry typeConverterRegistry) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setInjector(Injector injector) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addLifecycleStrategy(LifecycleStrategy lifecycleStrategy) {
        // do nothing
    }

    @Override
    public void setPropertiesComponent(PropertiesComponent propertiesComponent) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RouteController getInternalRouteController() {
        return new RouteController() {
            @Override
            public Collection<Route> getControlledRoutes() {
                return routes;
            }

            @Override
            public void startAllRoutes() throws Exception {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isStartingRoutes() {
                return false;
            }

            @Override
            public ServiceStatus getRouteStatus(String routeId) {
                return ServiceStatus.Started;
            }

            @Override
            public void startRoute(String routeId) throws Exception {
                throw new UnsupportedOperationException();
            }

            @Override
            public void stopRoute(String routeId) throws Exception {
                throw new UnsupportedOperationException();
            }

            @Override
            public void stopRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean stopRoute(String routeId, long timeout, TimeUnit timeUnit, boolean abortAfterTimeout)
                    throws Exception {
                throw new UnsupportedOperationException();
            }

            @Override
            public void suspendRoute(String routeId) throws Exception {
                throw new UnsupportedOperationException();
            }

            @Override
            public void suspendRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception {
                throw new UnsupportedOperationException();
            }

            @Override
            public void resumeRoute(String routeId) throws Exception {
                throw new UnsupportedOperationException();
            }

            @Override
            public void setCamelContext(CamelContext camelContext) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CamelContext getCamelContext() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void start() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void stop() {
                throw new UnsupportedOperationException();
            }
        };
    }

    protected void startService(Object object) throws Exception {
        // and register startup aware so they can be notified when
        // camel context has been started
        if (object instanceof StartupListener) {
            StartupListener listener = (StartupListener) object;
            addStartupListener(listener);
        }
        if (object instanceof CamelContextAware) {
            CamelContextAware aware = (CamelContextAware) object;
            aware.setCamelContext(reference);
        }
        if (object instanceof Service) {
            Service service = (Service) object;
            service.start();
        }
    }
}
