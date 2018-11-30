package com.meluzin.tibcobwutils.earcomparer;

import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;

import com.meluzin.functional.Lists;
import com.meluzin.tibcobwutils.earcomparer.expressionresolver.ConfigFileLoader;
import com.meluzin.tibcobwutils.earcomparer.expressionresolver.ConfigFileLoaderBuilder;
import com.meluzin.tibcobwutils.earcomparer.expressionresolver.EmptyArgumentAction;
import com.meluzin.tibcobwutils.earcomparer.expressionresolver.FileTemplateLoader;

import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.Version;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.MutuallyExclusiveGroup;
import net.sourceforge.argparse4j.inf.Namespace;

public class FullConfigExpressionResolver {
	public static final String COLUMNPROVIDER = "Provider";
	public static final String COLUMNCONFIGITEM = "ConfigItem";
	public static final String COLUMNVALUE = "Value";
	public static final char QUOTECHAR = '\'';
	public static final char SEPARATOR = ';';
	public static final char ESCAPECHAR = '"';

	private static final String ENCODING = "UTF-8";

	public static void main(String[] args) {
		ArgumentParser argParser = ArgumentParsers.newArgumentParser("FullConfigExpressionResolver", true, "-")
				.description("Takes FullConfig xml file and resolves expressions inside using global config file - used mainly for TED instances");
		argParser.addArgument("-fullconfig").type(String.class).required(true).help("Path to fullconfig that should be resolved.");
		MutuallyExclusiveGroup addMutuallyExclusiveGroup = argParser.addMutuallyExclusiveGroup().required(true);
		addMutuallyExclusiveGroup.addArgument("-fullconfigout").type(String.class).help("Path to output file with resolved full config.");
		EmptyArgumentAction onlyValidateAction = new EmptyArgumentAction();
		EmptyArgumentAction strictAction = new EmptyArgumentAction();
		EmptyArgumentAction verboseAction = new EmptyArgumentAction();
		addMutuallyExclusiveGroup.addArgument("-onlyvalidate").action(onlyValidateAction).setDefault(Arguments.SUPPRESS).help("Sets mode to validation only.");
		argParser.addArgument("-strict").action(strictAction).setDefault(Arguments.SUPPRESS).help("Sets error expression handling to strict mode - exception on error.");

		argParser.addArgument("-globalconfig").type(String.class).required(true).help("Path to global config CSV file.");
		
		argParser.addArgument("-providercolumn").type(String.class).required(false).setDefault(COLUMNPROVIDER).help("Name of Providers column.");
		argParser.addArgument("-configitemcolumn").type(String.class).required(false).setDefault(COLUMNCONFIGITEM).help("Name of Config Items column.");
		argParser.addArgument("-valuecolumn").type(String.class).required(false).setDefault(COLUMNVALUE).help("Name of Config Values column.");
		
		argParser.addArgument("-escapechar").type(Character.class).required(false).setDefault(ESCAPECHAR).help("Escape character in CSV global config.");
		argParser.addArgument("-separator").type(Character.class).required(false).setDefault(SEPARATOR).help("Separator character in CSV global config.");
		argParser.addArgument("-quotechar").type(Character.class).required(false).setDefault(QUOTECHAR).help("Quote character in CSV global config.");
		argParser.addArgument("-verbose").action(verboseAction).setDefault(Arguments.SUPPRESS).help("Sets verbose mode - more info in case of errors.");
		
		Namespace res = argParser.parseArgsOrFail(args);
		
		String providerColumnName = res.get("providercolumn");
		String configItemColumnName = res.get("configitemcolumn");
		String valueColumnName = res.get("valuecolumn");
		char escapeChar = res.get("escapechar");
		char separator = res.get("separator");
		char quoteChar = res.get("quotechar");
		String globalConfig = res.get("globalconfig");
		String fullConfig = res.get("fullconfig");
		String fullConfigOut = res.get("fullconfigout");
		
		boolean onlyvalidate = onlyValidateAction.isAvailable();
		boolean strict = strictAction.isAvailable();
		boolean verbose = verboseAction.isAvailable();
		Path config = Paths.get(globalConfig);
		if (!config.toFile().exists()) {
			throw new RuntimeException("File " + config + " does not exists");
		}
		ConfigFileLoaderBuilder configBuilder = new ConfigFileLoaderBuilder().
				setConfigItemColumnName(configItemColumnName).
				setEscapeChar(escapeChar).
				setProviderColumnName(providerColumnName).
				setQuoteChar(quoteChar).
				setSeparator(separator).
				setValueColumnName(valueColumnName);
		HashMap<String, Object> hashMap = loadConfigFile(configBuilder, config);


		try {
			processTemplate(fullConfig, fullConfigOut, onlyvalidate, strict, verbose, hashMap);
			System.exit(0);
		} catch (Exception e) {
			System.out.println(e.getMessage());
			if (verbose) {
				e.printStackTrace();
			}
			System.exit(1);
		}
	}

	public static void processTemplate(String fullConfig, String fullConfigOut, boolean onlyvalidate, boolean strict, boolean verbose,
			HashMap<String, Object> hashMap) throws IOException, TemplateException {
		try (Writer out = onlyvalidate ? new OutputStreamWriter(new ByteArrayOutputStream()) : new FileWriter(fullConfigOut)) {
		processTemplate(fullConfig, out, strict, verbose, hashMap);		
		}
	}

	public static void processTemplate(String fullConfig, Writer out, boolean strict, boolean verbose,
			HashMap<String, Object> hashMap) throws IOException, TemplateException {
		Configuration cfg = new Configuration(new Version(2, 3, 0));
		cfg.setLogTemplateExceptions(false);
		TemplateLoader templateLoader = new FileTemplateLoader();
		cfg.setTemplateExceptionHandler((e, env, output) -> {
			String replace = e.getFTLInstructionStack().replace("\n", "").replace("\r", "").replace("\t", "");
			if (strict) throw new RuntimeException("Error during processing template: " + replace + (verbose ? " " + e.getMessage() : ""), e);
			else System.out.println("Warning:" + replace + (verbose ? " " + e.getMessage() : ""));				
		});
		Template template = new Template(fullConfig,
				templateLoader.getReader(fullConfig, ENCODING), cfg);
		template.process(hashMap, out);
	}

	public static HashMap<String, Object> loadConfigFile(ConfigFileLoaderBuilder configBuilder,
			Path config) {
		return new ConfigFileLoader().loadConfigFile(configBuilder, config);
	}

	public static String concat(String... strings) {
		return Lists.join(strings, ",");
	}

	public static String concat(List<String> strings) {
		return Lists.join(strings, ",");
	}
}
