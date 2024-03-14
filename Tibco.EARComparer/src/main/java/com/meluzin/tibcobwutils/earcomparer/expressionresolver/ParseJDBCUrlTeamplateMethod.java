package com.meluzin.tibcobwutils.earcomparer.expressionresolver;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import freemarker.template.SimpleScalar;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;

final class ParseJDBCUrlTeamplateMethod implements TemplateMethodModelEx {
	@Override
	public Object exec(@SuppressWarnings("rawtypes") List arguments) throws TemplateModelException {
		// TODO Auto-generated method stub
		if (arguments.size() == 1 && arguments.get(0) instanceof SimpleScalar) {
			String url = ((SimpleScalar)arguments.get(0)).getAsString();
			Pattern compile = Pattern.compile("([a-z:]*)((\\[?)([^:/]*)/([^@\\]]*)(\\]?))?@(/?/?)([^/].*)");
			Matcher matcher = compile.matcher(url);
			if (matcher.find()) {
				
				String scheme = matcher.group(1);
				//String loginpassword = matcher.group(2);
				String login = matcher.group(4);
				String password = matcher.group(5);
				String restUrl = matcher.group(8);
				
				if (password != null && password.length() > 0 && (login == null || login.length() == 0)) {
					int lastIndexOf = scheme.lastIndexOf(":");
					login = scheme.substring(lastIndexOf + 1);
					scheme = scheme.substring(0, lastIndexOf + 1);
				}
				
				URI create = URI.create("jdbc://"+restUrl);
				HashMap<String, Object> urlMap = new HashMap<String, Object>();
				urlMap.put("host", create.getHost());
				urlMap.put("wholescheme", scheme);
				urlMap.put("scheme", create.getScheme());
				urlMap.put("port", ""+create.getPort());
				urlMap.put("path", create.getPath());
				urlMap.put("servicename", create.getPath().substring(1));
				urlMap.put("authority", create.getAuthority());
				urlMap.put("fragment", create.getFragment());
				urlMap.put("query", create.getQuery());
				urlMap.put("schemeSpecificPart", create.getSchemeSpecificPart());
				urlMap.put("userInfo", create.getUserInfo());
				if (login != null && login.length() > 0)
				urlMap.put("login", login);
				if (password != null && password.length() > 0)
				urlMap.put("password", password);
				return urlMap;
			} else {
				throw new TemplateModelException("Unknown URL format: " + url);
			}
				
		} else {
			throw new TemplateModelException("parseJDBCUrl requires one parameter containing JDBC URL, not ("+arguments+"). Ex. jdbc:oracle:thin:[APPTEST/dm1v93gtr]@rztvnode026.cz.tmo:1623/QSBL01.world");
		}
	}
}