package com.sap.hcp.cf.logging.servlet.filter;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.text.IsEmptyString.isEmptyOrNullString;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.MDC;

import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.JSONObjectException;
import com.sap.hcp.cf.logging.common.Defaults;
import com.sap.hcp.cf.logging.common.Fields;
import com.sap.hcp.cf.logging.common.HttpHeaders;
import com.sap.hcp.cf.logging.common.LogOptionalFieldsSettings;

public class RequestLoggingFilterTest {

	private static final String REQUEST_ID = "1234-56-7890-xxx";
	private static final String CORRELATION_ID = "xxx-56-7890-xxx";
	private static final String REQUEST = "/foobar";
	private static final String QUERY_STRING = "baz=bla";
	private static final String FULL_REQUEST = REQUEST + "?" + QUERY_STRING;
	private static final String REMOTE_HOST = "acme.org";
	private static final String REFERER = "my.fancy.com";

	@Rule
	public SystemOutRule systemOut = new SystemOutRule();

	@Rule
	public SystemErrRule systemErr = new SystemErrRule();

	private HttpServletRequest mockReq = mock(HttpServletRequest.class);
	private HttpServletResponse mockResp = mock(HttpServletResponse.class);
	private PrintWriter mockWriter = mock(PrintWriter.class);

	@Before
	public void initMocks() throws IOException {
		when(mockResp.getWriter()).thenReturn(mockWriter);

		Map<String, String> contextMap = new HashMap<>();
		Mockito.doAnswer(new Answer<Void>() {
			@SuppressWarnings("unchecked")
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				Object[] arguments = invocation.getArguments();
				contextMap.clear();
				contextMap.putAll((Map<? extends String, ? extends String>) arguments[1]);
				return null;
			}
		}).when(mockReq).setAttribute(eq(MDC.class.getName()), anyMapOf(String.class, String.class));

		when(mockReq.getAttribute(MDC.class.getName())).thenReturn(contextMap);
	}

	@Test
	public void testSimple() throws IOException, ServletException {
		FilterChain mockFilterChain = mock(FilterChain.class);
		new RequestLoggingFilter().doFilter(mockReq, mockResp, mockFilterChain);
		assertThat(getField(Fields.REQUEST), is(Defaults.UNKNOWN));
		assertThat(getField(Fields.CORRELATION_ID), not(isEmptyOrNullString()));
		assertThat(getField(Fields.REQUEST_ID), is(Defaults.UNKNOWN));
		assertThat(getField(Fields.REMOTE_HOST), is(Defaults.UNKNOWN));
		assertThat(getField(Fields.COMPONENT_ID), is(Defaults.UNKNOWN));
		assertThat(getField(Fields.CONTAINER_ID), is(Defaults.UNKNOWN));
		assertThat(getField(Fields.REQUEST_SIZE_B), is("-1"));
	}

	@Test
	public void testInputStream() throws IOException, ServletException {
		ServletInputStream mockStream = mock(ServletInputStream.class);

		when(mockReq.getInputStream()).thenReturn(mockStream);
		when(mockStream.read()).thenReturn(1);
		FilterChain mockFilterChain = new FilterChain() {
			@Override
			public void doFilter(ServletRequest request, ServletResponse response)
					throws IOException, ServletException {
				request.getInputStream().read();
			}
		};
		new RequestLoggingFilter().doFilter(mockReq, mockResp, mockFilterChain);
		assertThat(getField(Fields.REQUEST), is(Defaults.UNKNOWN));
		assertThat(getField(Fields.CORRELATION_ID), not(isEmptyOrNullString()));
		assertThat(getField(Fields.REQUEST_ID), is(Defaults.UNKNOWN));
		assertThat(getField(Fields.REMOTE_HOST), is(Defaults.UNKNOWN));
		assertThat(getField(Fields.COMPONENT_ID), is(Defaults.UNKNOWN));
		assertThat(getField(Fields.CONTAINER_ID), is(Defaults.UNKNOWN));
		assertThat(getField(Fields.REQUEST_SIZE_B), is("1"));
	}

	@Test
	public void testReader() throws IOException, ServletException {
		BufferedReader mockReader = mock(BufferedReader.class);

		when(mockReq.getReader()).thenReturn(mockReader);
		when(mockReader.read()).thenReturn(1);
		FilterChain mockFilterChain = new FilterChain() {
			@Override
			public void doFilter(ServletRequest request, ServletResponse response)
					throws IOException, ServletException {
				request.getReader().read();
			}
		};
		new RequestLoggingFilter().doFilter(mockReq, mockResp, mockFilterChain);
		assertThat(getField(Fields.REQUEST), is(Defaults.UNKNOWN));
		assertThat(getField(Fields.CORRELATION_ID), not(isEmptyOrNullString()));
		assertThat(getField(Fields.REQUEST_ID), is(Defaults.UNKNOWN));
		assertThat(getField(Fields.REMOTE_HOST), is(Defaults.UNKNOWN));
		assertThat(getField(Fields.COMPONENT_ID), is(Defaults.UNKNOWN));
		assertThat(getField(Fields.CONTAINER_ID), is(Defaults.UNKNOWN));
		assertThat(getField(Fields.REQUEST_SIZE_B), is("1"));
	}

	@Test
	public void testWithActivatedOptionalFields() throws IOException, ServletException {
		when(mockReq.getRequestURI()).thenReturn(REQUEST);
		when(mockReq.getQueryString()).thenReturn(QUERY_STRING);
		when(mockReq.getRemoteHost()).thenReturn(REMOTE_HOST);
		// will also set correlation id
		when(mockReq.getHeader(HttpHeaders.X_VCAP_REQUEST_ID)).thenReturn(REQUEST_ID);
		when(mockReq.getHeader(HttpHeaders.REFERER)).thenReturn(REFERER);
		FilterChain mockFilterChain = mock(FilterChain.class);
		LogOptionalFieldsSettings mockOptionalFieldsSettings = mock(LogOptionalFieldsSettings.class);
		when(mockOptionalFieldsSettings.isLogSensitiveConnectionData()).thenReturn(true);
		when(mockOptionalFieldsSettings.isLogRemoteUserField()).thenReturn(true);
		when(mockOptionalFieldsSettings.isLogRefererField()).thenReturn(true);
		RequestRecordFactory requestRecordFactory = new RequestRecordFactory(mockOptionalFieldsSettings);
		RequestLoggingFilter requestLoggingFilter = new RequestLoggingFilter(requestRecordFactory);
		requestLoggingFilter.doFilter(mockReq, mockResp, mockFilterChain);
		assertThat(getField(Fields.REQUEST), is(FULL_REQUEST));
		assertThat(getField(Fields.CORRELATION_ID), is(REQUEST_ID));
		assertThat(getField(Fields.REQUEST_ID), is(REQUEST_ID));
		assertThat(getField(Fields.REMOTE_HOST), is(REMOTE_HOST));
		assertThat(getField(Fields.COMPONENT_ID), is(Defaults.UNKNOWN));
		assertThat(getField(Fields.CONTAINER_ID), is(Defaults.UNKNOWN));
		assertThat(getField(Fields.REFERER), is(REFERER));
	}

	@Test
	public void testWithSuppressedOptionalFields() throws IOException, ServletException {
		when(mockReq.getRequestURI()).thenReturn(REQUEST);
		when(mockReq.getQueryString()).thenReturn(QUERY_STRING);
		when(mockReq.getRemoteHost()).thenReturn(REMOTE_HOST);
		// will also set correlation id
		when(mockReq.getHeader(HttpHeaders.X_VCAP_REQUEST_ID)).thenReturn(REQUEST_ID);
		when(mockReq.getHeader(HttpHeaders.REFERER)).thenReturn(REFERER);
		FilterChain mockFilterChain = mock(FilterChain.class);
		LogOptionalFieldsSettings mockLogOptionalFieldsSettings = mock(LogOptionalFieldsSettings.class);
		when(mockLogOptionalFieldsSettings.isLogSensitiveConnectionData()).thenReturn(false);
		when(mockLogOptionalFieldsSettings.isLogRemoteUserField()).thenReturn(false);
		when(mockLogOptionalFieldsSettings.isLogRefererField()).thenReturn(false);
		RequestRecordFactory requestRecordFactory = new RequestRecordFactory(mockLogOptionalFieldsSettings);
		RequestLoggingFilter requestLoggingFilter = new RequestLoggingFilter(requestRecordFactory);
		requestLoggingFilter.doFilter(mockReq, mockResp, mockFilterChain);
		assertThat(getField(Fields.REQUEST), is(FULL_REQUEST));
		assertThat(getField(Fields.CORRELATION_ID), is(REQUEST_ID));
		assertThat(getField(Fields.REQUEST_ID), is(REQUEST_ID));
		assertThat(getField(Fields.REMOTE_IP), is(Defaults.UNKNOWN));
		assertThat(getField(Fields.REMOTE_HOST), is(Defaults.REDACTED));
		assertThat(getField(Fields.COMPONENT_ID), is(Defaults.UNKNOWN));
		assertThat(getField(Fields.CONTAINER_ID), is(Defaults.UNKNOWN));
	}

	@Test
	public void testExplicitCorrelationId() throws IOException, ServletException {
		when(mockReq.getHeader(HttpHeaders.CORRELATION_ID)).thenReturn(CORRELATION_ID);
		when(mockReq.getHeader(HttpHeaders.X_VCAP_REQUEST_ID)).thenReturn(REQUEST_ID);
		FilterChain mockFilterChain = mock(FilterChain.class);
		new RequestLoggingFilter().doFilter(mockReq, mockResp, mockFilterChain);
		assertThat(getField(Fields.CORRELATION_ID), is(CORRELATION_ID));
		assertThat(getField(Fields.CORRELATION_ID), not(REQUEST_ID));
	}

	protected String getField(String fieldName) throws JSONObjectException, IOException {
		return JSON.std.mapFrom(getLastLine()).get(fieldName).toString();
	}

	private String getLastLine() {
		String[] lines = systemOut.toString().split("\n");
		return lines[lines.length - 1];
	}
}
