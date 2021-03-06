/*******************************************************************************
 * Copyright (c) 2016 IBM Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.ibm.liberty.starter.api.v1;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.validation.ValidationException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.xml.sax.SAXException;

import com.ibm.liberty.starter.PatternValidation;
import com.ibm.liberty.starter.ProjectZipConstructor;
import com.ibm.liberty.starter.ServiceConnector;
import com.ibm.liberty.starter.PatternValidation.PatternType;
import com.ibm.liberty.starter.ProjectZipConstructor.DeployType;
import com.ibm.liberty.starter.api.v1.model.internal.Services;
import com.ibm.liberty.starter.api.v1.model.registration.Service;

@Path("v1/data")
public class LibertyTechnologySelector {

    private static final Logger log = Logger.getLogger(LibertyTechnologySelector.class.getName());

    @GET
    @Produces("application/zip")
    public Response getResponse(@QueryParam("tech") String[] techs, @QueryParam("name") String name,
                                @QueryParam("deploy") final String deploy, @Context UriInfo info) throws NullPointerException, IOException {
        log.info("GET request for /data");
        try {
            final ServiceConnector serviceConnector = new ServiceConnector(info.getBaseUri());
            final List<Service> serviceList = new ArrayList<Service>();
            for (String tech : techs) {
                if (PatternValidation.checkPattern(PatternType.TECH, tech)) {
                    Service service = serviceConnector.getServiceObjectFromId(tech);
                    if (service != null) {
                        serviceList.add(service);
                    }
                } else {
                    throw new ValidationException("Invalid technology type.");
                }
            }
            if (name == null || name.length() == 0) {
                log.severe("No name passed in.");
                throw new ValidationException();
            }
            if (!PatternValidation.checkPattern(PatternType.NAME, name)) {
                log.severe("Invalid file name.");
                throw new ValidationException();
            }

            if (name.length() > 50) {
                log.severe("Invalid file name length.");
                throw new ValidationException();
            }

            if (deploy == null) {
                log.severe("No deploy type specified");
                throw new ValidationException();
            }
            final DeployType deployType = DeployType.valueOf(deploy.toUpperCase());

            final String appName = name;

            StreamingOutput so = (OutputStream os) -> {
                Services services = new Services();
                services.setServices(serviceList);
                ProjectZipConstructor projectZipConstructor = new ProjectZipConstructor(serviceConnector, services, appName, deployType);
                try {
                    projectZipConstructor.buildZip(os);
                } catch (SAXException | ParserConfigurationException | TransformerException e) {
                    throw new WebApplicationException(e);
                }
            };
            name += ".zip";
            return Response.ok(so, "application/zip").header("Content-Disposition", "attachment; filename=\"" + name + "\"").build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.FORBIDDEN).build();
        } catch (ValidationException e) {
            return Response.status(Status.BAD_REQUEST).entity("Validation of the input failed.").build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }
}
