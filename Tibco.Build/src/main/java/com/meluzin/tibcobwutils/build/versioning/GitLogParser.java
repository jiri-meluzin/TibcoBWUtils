package com.meluzin.tibcobwutils.build.versioning;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import com.meluzin.fluentxml.xml.builder.NodeBuilder;
import com.meluzin.fluentxml.xml.builder.XmlBuilderFactory;
import com.meluzin.functional.Log;
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
		GITLogGrammerVisitorImpl gitLogGrammerVisitorImpl = new GITLogGrammerVisitorImpl();
		List<ChangeInfo> listOfChanges = gitLogGrammerVisitorImpl.visitCreateLog(createLogCtx);
		return listOfChanges;
	}
	public NodeBuilder renderAsXml(List<ChangeInfo> changes) {

		XmlBuilderFactory fac = new XmlBuilderFactory();
		NodeBuilder changelog = fac.createRootElement("changeLog");
		changelog.addChildren(changes, (c, p) -> {
			p.addChild("logentry").
				addAttribute("revision", c.getRevisionInfo()).
				addChild("author").setTextContent(c.getAuthor()).getParent().
				addChild("date").setTextContent(Log.XSD_DATETIME_FORMATTER.format(c.getCommittedAt())).getParent().
				addChild("msg").setTextContent(c.getComment()).getParent().
				addChild("paths").addChildren(c.getChangedFiles(), (f,n) -> {
					n.addChild("path").addAttribute("original-file", f.getOriginalLocation().orElse(null)).addAttribute("status", f.getStatus()).setTextContent(f.getLocation());
				});
		});
		return changelog;
			
	}
}
