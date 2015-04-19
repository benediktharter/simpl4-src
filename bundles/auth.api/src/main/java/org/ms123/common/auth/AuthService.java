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
package org.ms123.common.auth.api;

import org.ms123.common.rpc.RpcException;
import java.util.Map;
import java.util.List;

public interface AuthService {
	public Map getUserProperties( String id);
	public String getAdminUser();
	public Map getUser(String id);
	public List<Map> getUserList();
	public Map getUserData(String d);
	public List<Map> getUserList(Map filter);
	public List<Map> getUserList(Map filter,int startIndex, int numResults);
}
