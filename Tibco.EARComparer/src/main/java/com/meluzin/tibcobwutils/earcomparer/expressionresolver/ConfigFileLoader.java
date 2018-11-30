package com.meluzin.tibcobwutils.earcomparer.expressionresolver;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;


public class ConfigFileLoader {
	public HashMap<String, Object> loadConfigFile(ConfigFileLoaderBuilder configBuilder,
			Path configFile) {
		try {
			File file = configFile.toFile();
			FileReader filereader = new FileReader(file);
			CSVParser csvParser = new CSVParserBuilder().
					withSeparator(configBuilder.getSeparator()).
					withEscapeChar(configBuilder.getEscapeChar()).
					withQuoteChar(configBuilder.getQuoteChar()).
					build();
			CSVReader csvReader = new CSVReaderBuilder(filereader).
					withCSVParser(csvParser).
					build();

			String[] nextRecord;
			String[] columnsRecord;
			Map<String, Integer> columnMap = new HashMap<>();

			// we are going to read data line by line
			if ((columnsRecord = csvReader.readNext()) == null) {
				throw new RuntimeException("Could not parse header from config file");
			}
			for (int i = 0; i < columnsRecord.length; i++) {
				String string = columnsRecord[i];
				columnMap.put(string, i);
			}
			HashMap<String, Object> hashMap = new HashMap<>();
			
			while ((nextRecord = csvReader.readNext()) != null) {
				String provider = nextRecord[columnMap.get(configBuilder.getProviderColumnName())];
				if (!hashMap.containsKey(provider)) {
					hashMap.put(provider, new HashMap<>());
				}
				@SuppressWarnings("unchecked")
				HashMap<String, Object> providerItemsMap = (HashMap<String, Object>)hashMap.get(provider);
				String configItemName = nextRecord[columnMap.get(configBuilder.getConfigItemColumnName())];
				String configItemValue = nextRecord[columnMap.get(configBuilder.getValueColumnName())];
				providerItemsMap.put(configItemName, configItemValue);
				hashMap.put(configItemName, configItemValue);
			}
			HashMap<String, Object> ret = new HashMap<String, Object>();
			ret.put("parseJDBCUrl", new ParseJDBCUrlTeamplateMethod());
			ret.put("jdbc", new ParseJDBCUrlTeamplateMethod());
			ret.putAll(hashMap);
			return ret;
		} catch (IOException e1) {
			throw new RuntimeException("Could not load config file (" + configFile + ")", e1);
		}
	}
	
	
	
}
