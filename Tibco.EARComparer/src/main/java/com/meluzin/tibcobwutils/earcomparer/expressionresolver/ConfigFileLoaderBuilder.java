package com.meluzin.tibcobwutils.earcomparer.expressionresolver;

import com.meluzin.tibcobwutils.earcomparer.FullConfigExpressionResolver;

public class ConfigFileLoaderBuilder {
	private String providerColumnName = FullConfigExpressionResolver.COLUMNPROVIDER;
	private String configItemColumnName = FullConfigExpressionResolver.COLUMNCONFIGITEM; 
	private String valueColumnName = FullConfigExpressionResolver.COLUMNVALUE; 
	private char escapeChar = FullConfigExpressionResolver.ESCAPECHAR; 
	private char separator = FullConfigExpressionResolver.SEPARATOR;
	private char quoteChar = FullConfigExpressionResolver.QUOTECHAR;
	public ConfigFileLoaderBuilder() {
	}
	public ConfigFileLoaderBuilder(String providerColumnName, String configItemColumnName, String valueColumnName,
			char escapeChar, char separator, char quoteChar) {
		super();
		this.providerColumnName = providerColumnName;
		this.configItemColumnName = configItemColumnName;
		this.valueColumnName = valueColumnName;
		this.escapeChar = escapeChar;
		this.separator = separator;
		this.quoteChar = quoteChar;
	}
	public String getProviderColumnName() {
		return providerColumnName;
	}
	public ConfigFileLoaderBuilder setProviderColumnName(String providerColumnName) {
		return new ConfigFileLoaderBuilder(providerColumnName, configItemColumnName, valueColumnName, escapeChar, separator, quoteChar);
	}
	public String getConfigItemColumnName() {
		return configItemColumnName;
	}
	public ConfigFileLoaderBuilder setConfigItemColumnName(String configItemColumnName) {
		return new ConfigFileLoaderBuilder(providerColumnName, configItemColumnName, valueColumnName, escapeChar, separator, quoteChar);
	}
	public String getValueColumnName() {
		return valueColumnName;
	}
	public ConfigFileLoaderBuilder setValueColumnName(String valueColumnName) {
		return new ConfigFileLoaderBuilder(providerColumnName, configItemColumnName, valueColumnName, escapeChar, separator, quoteChar);
	}
	public char getEscapeChar() {
		return escapeChar;
	}
	public ConfigFileLoaderBuilder setEscapeChar(char escapeChar) {
		return new ConfigFileLoaderBuilder(providerColumnName, configItemColumnName, valueColumnName, escapeChar, separator, quoteChar);
	}
	public char getSeparator() {
		return separator;
	}
	public ConfigFileLoaderBuilder setSeparator(char separator) {
		return new ConfigFileLoaderBuilder(providerColumnName, configItemColumnName, valueColumnName, escapeChar, separator, quoteChar);
	}
	public char getQuoteChar() {
		return quoteChar;
	}
	public ConfigFileLoaderBuilder setQuoteChar(char quoteChar) {
		return new ConfigFileLoaderBuilder(providerColumnName, configItemColumnName, valueColumnName, escapeChar, separator, quoteChar);
	}
}
