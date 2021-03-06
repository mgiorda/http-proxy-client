package com.http.proxy.impl;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import com.google.gson.Gson;
import com.http.model.FilePayload;
import com.http.model.FormRequest;
import com.http.model.HttpFactory;
import com.http.model.HttpMethod;
import com.http.model.HttpRequest;
import com.http.model.HttpResponse;
import com.http.model.QueryRequest;
import com.http.model.RawPayload;
import com.http.model.RequestParams;
import com.http.model.UrlEncodedPayload;
import com.http.model.curl.CurlLogger;
import com.http.proxy.ApiMetadataHandler;
import com.http.proxy.ApiResultHandler;
import com.http.proxy.BaseServiceMetadata;

public class ApiMethodHandlerImpl extends AbstractMethodHandler {

	private final ApiMetadataHandler metadataHandler;

	private final HttpFactory requestFactory;

	private final ApiResultHandler resultHandler;

	public ApiMethodHandlerImpl(ApiMetadataHandler metadataHandler, HttpFactory requestFactory, ApiResultHandler resultHandler) {

		if (metadataHandler == null || requestFactory == null || resultHandler == null) {

			throw new IllegalArgumentException("MetadataHandler, requestFactory and resultHandler cannot be null");
		}
		this.metadataHandler = metadataHandler;
		this.resultHandler = resultHandler;
		this.requestFactory = requestFactory;
	}

	public Object handleCall(Method method, Object[] args) throws Exception {

		BaseServiceMetadata metadata = metadataHandler.getBaseServiceMetadata(method);
		HttpRequest request = requestFactory.newRequest(metadata.getMethod(), metadata.getUrl());

		Map<String, String> pathParams = metadataHandler.getPathParams(method, args);
		request.setPathParams(pathParams);

		HttpMethod httpMethod = metadata.getMethod();

		if (httpMethod != HttpMethod.OPTIONS) {
			RequestParams queryParams = metadataHandler.getQueryParams(method, args);
			QueryRequest queryRequest = (QueryRequest) request;
			queryRequest.addQueryParams(queryParams);
		}

		if ((httpMethod == HttpMethod.POST || httpMethod == HttpMethod.PUT)) {

			FormRequest formRequest = (FormRequest) request;

			if (args.length == 0 && metadata.getConsumes().isEmpty()) {
				throw new IllegalStateException("Payload request cannot be called without parameters");
			}

			List<String> consumes = metadata.getConsumes();

			if (consumes.contains(MediaType.APPLICATION_JSON)) {

				String json = null;
				Object lastArgument = args[args.length - 1];
				if (lastArgument.getClass().isAssignableFrom(String.class)) {
					json = String.valueOf(lastArgument);
				} else {
					json = new Gson().toJson(lastArgument);
				}

				formRequest.setPayload(RawPayload.JSON(json));

			} else if (consumes.contains(MediaType.APPLICATION_OCTET_STREAM)) {
				Object lastArgument = args[args.length - 1];

				File payload = (File) lastArgument;
				formRequest.setPayload(new FilePayload(payload));

			} else if (consumes.contains(MediaType.APPLICATION_FORM_URLENCODED)) {

				RequestParams formParams = metadataHandler.getFormParams(method, args);
				formRequest.setPayload(new UrlEncodedPayload(formParams));
			} else {
				// TODO incorporar tipo Multipart
				throw new UnsupportedOperationException(String.format("@Consumes type %s not supported yet", consumes));
			}
		}

		CurlLogger.logRequest(request);
		HttpResponse response = request.send();
		CurlLogger.logResponse(response);

		String produces = metadata.getProduces();
		Type returnType = metadata.getResultType();

		return resultHandler.getResponseResult(response, produces, returnType);
	}
}
