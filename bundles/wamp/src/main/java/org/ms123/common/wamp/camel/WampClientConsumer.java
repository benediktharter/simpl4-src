/**
 * This file is part of SIMPL4(http://simpl4.org).
 *
 * 	Copyright [2014] [Manfred Sattler] <manfred@ms123.org>
 *
 * SIMPL4 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SIMPL4 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SIMPL4.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.ms123.common.wamp.camel;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.Processor;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.exceptions.OnErrorThrowable;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Observer;
import rx.Scheduler;
import rx.schedulers.Schedulers;
import rx.subjects.AsyncSubject;
import rx.subjects.BehaviorSubject;
import rx.Subscriber;
import rx.Subscription;
import rx.subscriptions.CompositeSubscription;
import rx.subscriptions.Subscriptions;
import org.ms123.common.wamp.WampClientSession;
import org.ms123.common.wamp.ApplicationError;
import org.ms123.common.wamp.Request;
import org.apache.camel.AsyncCallback;
import org.apache.camel.ExchangePattern;
import org.ms123.common.system.thread.ThreadContext;
import org.ms123.common.permission.api.PermissionService;
import org.apache.camel.CamelContext;
import static org.ms123.common.wamp.camel.WampClientConstants.*;
import org.apache.commons.beanutils.ConvertUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

public class WampClientConsumer extends DefaultConsumer {

	private static final Logger LOG = LoggerFactory.getLogger(WampClientConsumer.class);
	private final WampClientEndpoint endpoint;
	private WampClientSession clientSession;
	private Processor processor;
	private PermissionService permissionService;
	private ObjectMapper objectMapper = new ObjectMapper();
	protected static final Map<String, Class> types = new HashMap<String, Class>() {

		{
			put("string", java.lang.String.class);
			put("integer", java.lang.Integer.class);
			put("double", java.lang.Double.class);
			put("boolean", java.lang.Boolean.class);
			put("map", java.util.Map.class);
			put("list", java.util.List.class);
		}
	};

	public WampClientConsumer(WampClientEndpoint endpoint, Processor processor) {
		super(endpoint, processor);
		this.endpoint = endpoint;
		this.processor = processor;
	}

	private void wampClientConnected() {
		info("register:" + endpoint.getProcedure());
		Subscription addProcSubscription = this.clientSession.registerProcedure(endpoint.getProcedure()).subscribe((request) -> {

			info("Request:" + request);
			final boolean reply = false;
			final Exchange exchange = endpoint.createExchange(reply ? ExchangePattern.InOut : ExchangePattern.InOnly);
			prepareExchange(exchange, request);
			exchange.getIn().setBody("Hello from Endpoint");
			try {
				getAsyncProcessor().process(exchange, new AsyncCallback() {

					@Override
					public void done(boolean doneSync) {
						info("Body:" + exchange.getOut().getBody());
						request.reply(exchange.getOut().getBody());
					}
				});
			} catch (Exception e) {
				getExceptionHandler().handleException("Error processing Wamp event: " + exchange, e);
			}

			/*if (request.arguments() == null || request.arguments().size() != 2 || !request.arguments().get(0).canConvertToLong() || !request.arguments().get(1).canConvertToLong()) {
				try {
					request.replyError(new ApplicationError(ApplicationError.INVALID_PARAMETER));
				} catch (ApplicationError e) {
					e.printStackTrace();
				}
			} else {
				long a = request.arguments().get(0).asLong();
				long b = request.arguments().get(1).asLong();
				request.reply(a + b);
			}*/
		});

		Observable<Long> result1 = this.clientSession.call("com.myapp.add2", Long.class, 33, 66);
		result1.subscribe((t2) -> {
			System.out.println("Completed add with result " + t2);
		}, (t3) -> {
			System.out.println("Completed add with error " + t3);
		});
	}

	private void prepareExchange(Exchange exchange, Request request) {
		if (this.permissionService == null) {
			this.permissionService = getByType(exchange.getContext(), PermissionService.class);
		}
		Map<String, Object> methodParams = objectMapper.convertValue(request.keywordArguments(), Map.class);
		List<String> permittedRoleList = this.endpoint.getPermittedRoles();
		List<String> permittedUserList = this.endpoint.getPermittedUsers();
		String userName = getUserName();
		List<String> userRoleList = getUserRoles(userName);
		debug("userName:" + userName);
		debug("userRoleList:" + userRoleList);
		debug("permittedRoleList:" + permittedRoleList);
		debug("permittedUserList:" + permittedUserList);
		if (!isPermitted(userName, userRoleList, permittedUserList, permittedRoleList)) {
			throw new RuntimeException(PERMISSION_DENIED + ":User(" + userName + ") has no permission");
		}

		Map<String, Object> properties = new HashMap<>();
		Map<String, Object> headers = new HashMap<>();
		Map<String, Object> bodyMap = new HashMap<>();
		Object bodyObj = null;
		List<Map> paramList = this.endpoint.getParamList();
		int bodyCount = countBodyParams(paramList);
		for (Map param : paramList) {
			String destination = (String) param.get("destination");
			String name = (String) param.get("name");
			Object def = param.get("defaultvalue");
			Class type = this.types.get((String) param.get("type"));
			Boolean opt = (Boolean) param.get("optional");
			if ("property".equals(destination)) {
				properties.put(name, getValue(name, methodParams.get(name), def, opt, type));
			} else if ("header".equals(destination)) {
				headers.put(name, getValue(name, methodParams.get(name), def, opt, type));
			} else if ("body".equals(destination)) {
				bodyObj = getValue(name, methodParams.get(name), def, opt, type);
				bodyMap.put(name, bodyObj);
			}
		}

		if (bodyCount != 1) {
			if (bodyMap.keySet().size() > 0) {
				bodyObj = bodyMap;
			} else {
				bodyObj = null;
			}
		}
		//properties.put("__logExceptionsOnly", getBoolean(shape, "logExceptionsOnly", false));
		debug("methodParams:" + methodParams);
		debug("paramList:" + paramList);
		debug("properties:" + properties);
		debug("headers:" + headers);

		String returnSpec = this.endpoint.getRpcReturn();
		List<String> returnHeaderList = new ArrayList();
		List<Map> rh = this.endpoint.getReturnHeaders();
		if (rh != null) {
			for (Map<String, String> m : rh) {
				returnHeaderList.add(m.get("name"));
			}
		}
	}

	protected String getUserName() {
		return ThreadContext.getThreadContext().getUserName();
	}

	protected boolean isPermitted(String userName, List<String> userRoleList, List<String> permittedUserList, List<String> permittedRoleList) {
		if (permittedUserList.contains(userName)) {
			info("userName(" + userName + " is allowed:" + permittedUserList);
			return true;
		}
		for (String userRole : userRoleList) {
			if (permittedRoleList.contains(userRole)) {
				info("userRole(" + userRole + " is allowed:" + permittedRoleList);
				return true;
			}
		}
		return false;
	}

	protected List<String> getUserRoles(String userName) {
		List<String> userRoleList = null;
		try {
			userRoleList = this.permissionService.getUserRoles(userName);
		} catch (Exception e) {
			userRoleList = new ArrayList<>();
		}
		return userRoleList;
	}

	protected Object getValue(String name, Object value, Object def, boolean optional, Class type) {
		if (value == null && def != null) {
			def = convertTo(def, type);
			value = def;
		}
		if (value == null && optional == false) {
			throw new RuntimeException("CamelRouteService:Missing parameter:" + name);
		}
		if (value == null) {
			return null;
		}
		if (!type.isAssignableFrom(value.getClass())) {
			throw new RuntimeException("CamelRouteService:parameter(" + name + ") wrong type:" + value.getClass() + " needed:" + type);
		}
		return value;
	}

	public static Object convertTo(Object sourceObject, Class<?> targetClass) {
		try {
			return ConvertUtils.convert(sourceObject, targetClass);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	protected int countBodyParams(List<Map> paramList) {
		int count = 0;
		for (Map param : paramList) {
			String destination = (String) param.get("destination");
			if ("body".equals(destination)) {
				count++;
			}
		}
		return count;
	}

	private <T> T getByType(CamelContext ctx, Class<T> kls) {
		return kls.cast(ctx.getRegistry().lookupByName(kls.getName()));
	}

	protected void doStart() throws Exception {
		this.clientSession = endpoint.createWampClientSession("realm1");
		this.clientSession.statusChanged().subscribe((state) -> {
			info("ClientSession:status changed to " + state);
			if (state == WampClientSession.Status.Connected) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}
				wampClientConnected();
			}
			if (state == WampClientSession.Status.Disconnected) {
				try {
					this.doStop();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}, (t) -> {
			debug("ClientSession ended with error " + t);
		}, () -> {
			debug("clientSession ended normally");
		});
		super.doStart();
	}

	protected void doStop() throws Exception {
		debug("Stopping WampClientConsumer " + endpoint.getProcedure());
		this.clientSession.close();
		this.clientSession = null;
		super.doStop();
	}

	protected void debug(String msg, Object... args) {
		System.out.println(MessageFormatter.arrayFormat(msg, varargsToArray(args)).getMessage());
		LOG.debug(msg, args);
	}

	protected void info(String msg, Object... args) {
		System.out.println(MessageFormatter.arrayFormat(msg, varargsToArray(args)).getMessage());
		LOG.info(msg, args);
	}

	private Object[] varargsToArray(Object... args) {
		Object[] ret = new Object[args.length];
		for (int i = 0; i < args.length; i++) {
			ret[i] = args[i];
		}
		return ret;
	}
}
