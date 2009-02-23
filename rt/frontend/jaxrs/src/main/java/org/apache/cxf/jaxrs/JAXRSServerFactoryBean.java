/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.jaxrs;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import org.apache.cxf.BusException;
import org.apache.cxf.common.i18n.BundleUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.endpoint.EndpointException;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.endpoint.ServerImpl;
import org.apache.cxf.feature.AbstractFeature;
import org.apache.cxf.jaxrs.impl.RequestPreprocessor;
import org.apache.cxf.jaxrs.lifecycle.PerRequestResourceProvider;
import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.jaxrs.provider.ProviderFactory;
import org.apache.cxf.jaxrs.utils.InjectionUtils;
import org.apache.cxf.service.factory.ServiceConstructionException;
import org.apache.cxf.service.invoker.Invoker;


/**
 * Bean to help easily create Server endpoints for JAX-RS. Example:
 * <pre>
 * JAXRSServerFactoryBean sf = JAXRSServerFactoryBean();
 * sf.setResourceClasses(Book.class);
 * sf.setBindingId(JAXRSBindingFactory.JAXRS_BINDING_ID);
 * sf.setAddress("http://localhost:9080/");
 * sf.create();
 * </pre>
 * This will start a server for you and register it with the ServerManager.
 */
public class JAXRSServerFactoryBean extends AbstractJAXRSFactoryBean {
    
    private static final Logger LOG = LogUtils.getL7dLogger(JAXRSServerFactoryBean.class);
    private static final ResourceBundle BUNDLE = BundleUtils.getBundle(JAXRSServerFactoryBean.class);
    
    protected Map<Class, ResourceProvider> resourceProviders = new HashMap<Class, ResourceProvider>();
    
    private Server server;
    private Invoker invoker;
    private boolean start = true;
    private List<Object> serviceBeans;
    private List<?> entityProviders;
    private Map<Object, Object> languageMappings;
    private Map<Object, Object> extensionMappings;
    private List<String> schemaLocations;
    
    public JAXRSServerFactoryBean() {
        this(new JAXRSServiceFactoryBean());
    }

    public JAXRSServerFactoryBean(JAXRSServiceFactoryBean sf) {
        super(sf);
    }
    
    public void setSchemaLocations(List<String> schemas) {
        this.schemaLocations = schemas;    
    }
    
    public void setStaticSubresourceResolution(boolean enableStatic) {
        serviceFactory.setEnableStaticResolution(enableStatic);
    }
    
    public Server create() {
        try {
            if (!serviceFactory.resourcesAvailable()) {
                org.apache.cxf.common.i18n.Message msg = 
                    new org.apache.cxf.common.i18n.Message("NO_RESOURCES_AVAILABLE", 
                                                           BUNDLE);
                LOG.severe(msg.toString());
                throw new EndpointException(msg);
            }
            if (serviceFactory.getService() == null) {
                serviceFactory.create();
                updateClassResourceProviders();
            }
            
            Endpoint ep = createEndpoint();
            server = new ServerImpl(getBus(), 
                                    ep, 
                                    getDestinationFactory(), 
                                    getBindingFactory());

            if (invoker == null) {
                ep.getService().setInvoker(createInvoker());
            } else {
                ep.getService().setInvoker(invoker);
            }
            if (entityProviders != null) {
                ProviderFactory.getInstance(getAddress()).setUserProviders(entityProviders); 
            }
            ProviderFactory.getInstance(getAddress()).setRequestPreprocessor(
                new RequestPreprocessor(languageMappings, extensionMappings));
            if (schemaLocations != null) {
                ProviderFactory.getInstance(getAddress()).setSchemaLocations(schemaLocations);
            }
            
            if (start) {
                server.start();
            }
        } catch (EndpointException e) {
            throw new ServiceConstructionException(e);
        } catch (BusException e) {
            throw new ServiceConstructionException(e);
        } catch (IOException e) {
            throw new ServiceConstructionException(e);
        }

        applyFeatures();
        return server;
    }

    protected void applyFeatures() {
        if (getFeatures() != null) {
            for (AbstractFeature feature : getFeatures()) {
                feature.initialize(server, getBus());
            }
        }
    }

    protected Invoker createInvoker() {
        if (serviceBeans == null) {
            return new JAXRSInvoker();
        } else {
            return new JAXRSInvoker(serviceBeans);           
        }
    }

    public void setLanguageMappings(Map<Object, Object> lMaps) {
        languageMappings = lMaps;
    }
    
    public void setExtensionMappings(Map<Object, Object> extMaps) {
        extensionMappings = extMaps;
    }
    
    public JAXRSServiceFactoryBean getServiceFactory() {
        return serviceFactory;
    }

    public void setServiceFactory(JAXRSServiceFactoryBean serviceFactory) {
        this.serviceFactory = serviceFactory;
    }
    
    public List<Class> getResourceClasses() {
        return serviceFactory.getResourceClasses();
    }

    public void setResourceClasses(List<Class> classes) {
        serviceFactory.setResourceClasses(classes);
    }

    public void setResourceClasses(Class... classes) {
        serviceFactory.setResourceClasses(classes);
    }
    
    /**
     * Set the backing service bean. If this is set, JAX-RS runtime will not be
     * responsible for the lifecycle of resource classes.
     * 
     * @return
     */
    public void setServiceBeans(Object... beans) {
        setServiceBeans(Arrays.asList(beans));
    }
    
    public void setServiceBeans(List<Object> beans) {
        this.serviceBeans = beans;
        serviceFactory.setResourceClassesFromBeans(beans);
    }
    
    public void setResourceProvider(Class c, ResourceProvider rp) {
        resourceProviders.put(c, rp);
    }

    /**
     * @return the entityProviders
     */
    public List<?> getProviders() {
        return entityProviders;
    }

    /**
     * @param entityProviders the entityProviders to set
     */
    public void setProviders(List<? extends Object> providers) {
        this.entityProviders = providers;
    }
    
    public void setInvoker(Invoker invoker) {
        this.invoker = invoker;
    }

    public void setStart(boolean start) {
        this.start = start;
    }

    private void injectContexts() {
        for (ClassResourceInfo cri : serviceFactory.getClassResourceInfo()) {
            if (cri.isSingleton()) {
                InjectionUtils.injectContextProxies(cri, 
                                                    cri.getResourceProvider().getInstance());
            }
        }
    }
    
    private void updateClassResourceProviders() {
        for (ClassResourceInfo cri : serviceFactory.getClassResourceInfo()) {
            if (cri.getResourceProvider() != null) {
                continue;
            }
            
            ResourceProvider rp = resourceProviders.get(cri.getResourceClass());
            if (rp != null) {
                cri.setResourceProvider(rp);
            } else {
                //default lifecycle is per-request
                rp = new PerRequestResourceProvider(cri.getResourceClass());
                cri.setResourceProvider(rp);  
            }
        }
        injectContexts();
    }
}
