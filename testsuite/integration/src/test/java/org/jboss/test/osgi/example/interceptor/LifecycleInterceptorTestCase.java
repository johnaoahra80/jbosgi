/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.test.osgi.example.interceptor;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;

import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.logging.Logger;
import org.jboss.osgi.deployment.interceptor.LifecycleInterceptor;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.osgi.spi.AttachmentKey;
import org.jboss.osgi.vfs.VirtualFile;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.HttpRequest;
import org.jboss.test.osgi.example.interceptor.bundle.EndpointServlet;
import org.jboss.test.osgi.example.interceptor.bundle.HttpMetadata;
import org.jboss.test.osgi.example.interceptor.bundle.InterceptorActivator;
import org.jboss.test.osgi.example.interceptor.bundle.ParserInterceptor;
import org.jboss.test.osgi.example.interceptor.bundle.PublisherInterceptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.http.HttpService;

/**
 * A test that deployes a bundle that contains some metadata and an interceptor bundle that processes the metadata and
 * registeres an http endpoint from it.
 *
 * The idea is that the bundle does not process its own metadata. Instead this work is delegated to some specialized metadata
 * processor (i.e. the interceptor)
 *
 * @author thomas.diesler@jboss.com
 * @since 23-Oct-2009
 */
@RunWith(Arquillian.class)
public class LifecycleInterceptorTestCase {

    static final String PROCESSOR_NAME = "interceptor-processor";
    static final String ENDPOINT_NAME = "interceptor-endpoint";

    @ArquillianResource
    Deployer deployer;

    @ArquillianResource
    ManagementClient managementClient;

    @ArquillianResource
    BundleContext context;

    @Deployment
    public static JavaArchive createdeployment() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "example-interceptor");
        archive.addClasses(HttpRequest.class);
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addImportPackages(HttpService.class, ManagementClient.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    @Test
    public void testServletAccess() throws Exception {
        Bundle procBundle = context.installBundle(PROCESSOR_NAME, deployer.getDeployment(PROCESSOR_NAME));
        try {
            procBundle.start();
            Bundle endpointBundle = context.installBundle(ENDPOINT_NAME, deployer.getDeployment(ENDPOINT_NAME));
            try {
                endpointBundle.start();
                String line = getHttpResponse("/example-interceptor/servlet");
                assertEquals("Hello from Servlet", line);
            } finally {
                endpointBundle.uninstall();
            }
        } finally {
            procBundle.uninstall();
        }
    }

    @Deployment(name = PROCESSOR_NAME, managed = false, testable = false)
    public static JavaArchive getProcessorArchive() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, PROCESSOR_NAME);
        archive.addClasses(HttpMetadata.class, InterceptorActivator.class, ParserInterceptor.class, PublisherInterceptor.class);
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addBundleActivator(InterceptorActivator.class);
                builder.addImportPackages(BundleActivator.class, LifecycleInterceptor.class, HttpService.class);
                builder.addImportPackages(HttpServlet.class, Servlet.class, Logger.class, VirtualFile.class);
                builder.addImportPackages(AttachmentKey.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    @Deployment(name = ENDPOINT_NAME, managed = false, testable = false)
    public static JavaArchive getEndpointArchive() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, ENDPOINT_NAME);
        archive.addClasses(EndpointServlet.class);
        archive.addAsResource("interceptor/http-metadata.properties", "http-metadata.properties");
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addImportPackages(HttpServlet.class, Servlet.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private String getHttpResponse(String path) throws Exception {
        String urlspec = managementClient.getWebUri() + path;
        return HttpRequest.get(urlspec, 5, TimeUnit.SECONDS);
    }
}