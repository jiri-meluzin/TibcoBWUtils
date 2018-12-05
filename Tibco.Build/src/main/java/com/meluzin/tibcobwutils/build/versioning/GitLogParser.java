package com.meluzin.tibcobwutils.build.versioning;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import com.meluzin.tibcobwutils.build.versioning.GITLogGrammerParser.CreateLogContext;


public class GitLogParser implements VersionSystemLogParser {
	@Override
	public List<ChangeInfo> getChanges(InputStream logStream) {
		CharStream fromStream;
		try {
			fromStream = CharStreams.fromStream(logStream);
		} catch (IOException e) {
			throw new RuntimeException("Could not process stream", e);
		}
		GITLogGrammerLexer lexer = new GITLogGrammerLexer(fromStream);
		
		CommonTokenStream commonTokenStream = new CommonTokenStream(lexer);
		GITLogGrammerParser parser = new GITLogGrammerParser(commonTokenStream);
		CreateLogContext createLogCtx = parser.createLog();
		if (createLogCtx.exception != null) throw createLogCtx.exception;
		List<ChangeInfo> listOfChanges = new GITLogGrammerVisitorImpl().visitCreateLog(createLogCtx);
		return listOfChanges;
	}

}
