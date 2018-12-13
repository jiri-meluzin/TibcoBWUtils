package com.meluzin.tibcobwutils.build.versioning;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import com.meluzin.fluentxml.xml.builder.NodeBuilder;
import com.meluzin.fluentxml.xml.builder.XmlBuilderFactory;
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
		NodeBuilder changelog = fac.createRootElement("changelog");
		changelog.addChildren(changes, (c, p) -> {
			p.addChild("commit").
				addChild("revision").setTextContent(c.getRevisionInfo()).getParent().
				addChild("author").setTextContent(c.getAuthor()).getParent().
				addChild("date").setTextContent(c.getCommittedAt()).getParent().
				addChild("comment").setTextContent(c.getComment()).getParent().
				addChild("files").addChildren(c.getChangedFiles(), (f,n) -> {
					n.addChild("file").addAttribute("original-file", f.getOriginalLocation().orElse(null)).addAttribute("status", f.getStatus()).setTextContent(f.getLocation());
				});
		});
		return changelog;
			
	}
}
