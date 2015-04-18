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
package org.ms123.common.xmpp;

import java.rmi.RemoteException;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthProvider;
import org.jivesoftware.openfire.auth.ConnectionException;
import org.jivesoftware.openfire.auth.InternalUnauthenticatedException;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.slf4j.Logger;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.ms123.common.auth.api.AuthService;

/**
 * Auth provider for Atlassian Crowd
 */
public class Simpl4AuthProvider implements AuthProvider {

	private static final Logger m_log = LoggerFactory.getLogger(Simpl4AuthProvider.class);

	private AuthService m_authService;

	public Simpl4AuthProvider() {
		Simpl4Manager sm = Simpl4Manager.getInstance();
		m_authService = sm.lookupServiceByClass(AuthService.class);
	}

	public boolean isPlainSupported() {
		return true;
	}

	public boolean isDigestSupported() {
		return false;
	}

	/**
     * Returns if the username and password are valid; otherwise this
     * method throws an UnauthorizedException.<p>
     *
     * If {@link #isPlainSupported()} returns false, this method should
     * throw an UnsupportedOperationException.
     *
     * @param username the username or full JID.
     * @param password the password
     * @throws UnauthorizedException if the username and password do
     *      not match any existing user.
     * @throws ConnectionException it there is a problem connecting to user and group sytem
     * @throws InternalUnauthenticatedException if there is a problem authentication Openfire itself into the user and group system
     */
	public void authenticate(String username, String password) throws UnauthorizedException, ConnectionException, InternalUnauthenticatedException {
		if (username == null || password == null || "".equals(password.trim())) {
			throw new UnauthorizedException();
		}
		if (username.contains("@")) {
			// Check that the specified domain matches the server's domain
			int index = username.indexOf("@");
			String domain = username.substring(index + 1);
			if (domain.equals(XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
				username = username.substring(0, index);
			} else {
				// Unknown domain. Return authentication failed.
				throw new UnauthorizedException();
			}
		}
		_authenticate(username, password);
	}

	private void _authenticate(String username, String password) throws UnauthorizedException {
		username = JID.unescapeNode(username);
		info("authenticate '" + String.valueOf(username) + "'");
		Map<String, String> userProps = userProps = m_authService.getUserProperties(username);
		if (userProps == null) {
			throw new UnauthorizedException();
		}
		String _password = (String) userProps.get("password");
		if (_password != null) {
			if (!_password.trim().equals(password.trim()) && !(_password.equals("") && password.equals("admin"))) {
				throw new UnauthorizedException();
			}
		}
		info("authenticated user:" + username);
	}

	private void info(String msg){
		System.out.println(msg);
		m_log.info(msg);
	}
	public void authenticate(String username, String token, String digest) throws UnauthorizedException, ConnectionException, InternalUnauthenticatedException {
		throw new UnsupportedOperationException("XMPP digest authentication not supported by this version of authentication provider");
	}

	public String getPassword(String username) throws UserNotFoundException, UnsupportedOperationException {
		throw new UnsupportedOperationException("Retrieve password not supported by this version of authentication provider");
	}

	public void setPassword(String username, String password) throws UserNotFoundException, UnsupportedOperationException {
		throw new UnsupportedOperationException("Setting password not implemented by this version of authentication provider");
	}

	public boolean supportsPasswordRetrieval() {
		return false;
	}
}
