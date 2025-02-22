/*******************************************************************************
 * Copyright (c) 2021 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.web.sample.configuration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.XMLParserPool;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.emf.ecore.xmi.impl.XMLParserPoolImpl;
import org.eclipse.sirius.emfjson.resource.JsonResource;
import org.eclipse.sirius.web.emf.services.SiriusWebJSONResourceFactoryImpl;
import org.eclipse.sirius.web.emf.utils.EMFResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Helper to create a document stereotype from an EMF model.
 *
 * @author pcdavid
 */
public class StereotypeBuilder {
    private static XMLParserPool parserPool = new XMLParserPoolImpl();

    private final Logger logger = LoggerFactory.getLogger(StereotypeBuilder.class);

    private final Timer timer;

    public StereotypeBuilder(String timerName, MeterRegistry meterRegistry) {
        this.timer = Timer.builder(timerName).register(meterRegistry);
    }

    public String getStereotypeBody(EObject rootEObject) {
        JsonResource resource = new SiriusWebJSONResourceFactoryImpl().createResource(URI.createURI("inmemory")); //$NON-NLS-1$
        if (rootEObject != null) {
            resource.getContents().add(rootEObject);
        }

        String content = ""; //$NON-NLS-1$
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Map<String, Object> options = new HashMap<>();
            options.put(JsonResource.OPTION_ENCODING, JsonResource.ENCODING_UTF_8);
            options.put(JsonResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE);

            resource.save(outputStream, options);

            content = outputStream.toString();
        } catch (IOException exception) {
            this.logger.error(exception.getMessage(), exception);
        }
        return content;
    }

    public String getStereotypeBody(ClassPathResource classPathResource) {
        long start = System.currentTimeMillis();

        String content = ""; //$NON-NLS-1$
        try (var inputStream = classPathResource.getInputStream()) {
            URI uri = URI.createURI(classPathResource.getFilename());
            Resource inputResource = this.loadFromXMI(uri, inputStream);
            content = this.saveAsJSON(uri, inputResource);
        } catch (IOException exception) {
            this.logger.error(exception.getMessage(), exception);
        }

        long end = System.currentTimeMillis();
        this.timer.record(end - start, TimeUnit.MILLISECONDS);

        return content;
    }

    private Resource loadFromXMI(URI uri, InputStream inputStream) throws IOException {
        Resource inputResource = new XMIResourceImpl(uri);
        Map<String, Object> xmiLoadOptions = new EMFResourceUtils().getFastXMILoadOptions(parserPool);
        inputResource.load(inputStream, xmiLoadOptions);
        return inputResource;
    }

    private String saveAsJSON(URI uri, Resource inputResource) throws IOException {
        String content;
        JsonResource ouputResource = new SiriusWebJSONResourceFactoryImpl().createResource(uri);
        ouputResource.getContents().addAll(inputResource.getContents());
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Map<String, Object> jsonSaveOptions = new EMFResourceUtils().getFastJSONSaveOptions();
            jsonSaveOptions.put(JsonResource.OPTION_ENCODING, JsonResource.ENCODING_UTF_8);
            jsonSaveOptions.put(JsonResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE);
            ouputResource.save(outputStream, jsonSaveOptions);
            content = outputStream.toString(StandardCharsets.UTF_8);
        }
        return content;
    }
}
