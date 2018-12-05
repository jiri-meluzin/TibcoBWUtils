package com.meluzin.tibcobwutils.build.versioning;

import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import com.meluzin.fluentxml.xml.builder.NodeBuilder;
import com.meluzin.fluentxml.xml.builder.XmlBuilderFactory;
import com.meluzin.functional.Lists;
import com.meluzin.tibcobwutils.build.versioning.GITLogGrammerParser.AuthorLineContext;
import com.meluzin.tibcobwutils.build.versioning.GITLogGrammerParser.CommentContext;
import com.meluzin.tibcobwutils.build.versioning.GITLogGrammerParser.CommitLineContext;
import com.meluzin.tibcobwutils.build.versioning.GITLogGrammerParser.CommitRuleContext;
import com.meluzin.tibcobwutils.build.versioning.GITLogGrammerParser.DateLineContext;
import com.meluzin.tibcobwutils.build.versioning.GITLogGrammerParser.FileContext;

final class GITLogGrammerVisitorImpl extends GITLogGrammerBaseVisitor<List<ChangeInfo>> {
	List<String> commits = new ArrayList<>();
	XmlBuilderFactory fac = new XmlBuilderFactory();
	NodeBuilder gitlog = fac.createRootElement("gitlog");
	String author;
	String hash;
	Date date;
	List<String> comments = new ArrayList<>();
	List<String> files = new ArrayList<>();
	List<ChangeInfo> result = new ArrayList<>();

	GITLogGrammerVisitorImpl() {
	}

	@Override
	public List<ChangeInfo> visitFile(FileContext ctx) {
		super.visitFile(ctx);
		files.add(ctx.getChild(0).getText());
		return result;
	}

	@Override
	public List<ChangeInfo> visitDateLine(DateLineContext ctx) {
		super.visitDateLine(ctx);
		String d = ctx.getChild(0).getText().replace("Date:   ", "");
		DateFormat dateFormat = new SimpleDateFormat(
	            "EEE MMM d HH:mm:ss yyyy Z",  Locale.US);
		try {
			date = dateFormat.parse(d);
			
		} catch (ParseException e) {
			e.printStackTrace();
			date = null;
		}
		return result;
	}

	@Override
	public List<ChangeInfo> visitComment(CommentContext ctx) {
		super.visitComment(ctx);
		comments.add(ctx.getChild(0).toString().substring(4));
		return result;
	}

	public List<ChangeInfo> visitAuthorLine(AuthorLineContext ctx) {
		super.visitAuthorLine(ctx);
		author = ctx.getChild(0).toString().replace("Author: ", "");
		return result;
	}

	public List<ChangeInfo> visitCommitLine(CommitLineContext ctx) {
		super.visitCommitLine(ctx);
		hash = ctx.getChild(1).toString();
		comments.clear();
		files.clear();
		return result;
	}

	@Override
	public List<ChangeInfo> visitCommitRule(CommitRuleContext ctx) {
		super.visitCommitRule(ctx);
		// TODO Auto-generated method stub
		String h = hash + " -> " + author + " @ " + date + comments + files;
		String comment = comments.stream().collect(Collectors.joining("\n"));
		ChangeInfo changeInfo = ChangeInfo.builder().withAuthor(author).withRevisionInfo(hash).withComment(comment).withCommittedAt(date).withChangedFiles(files.stream().map(f -> {
			String[] parts = f.split("\t");
			return parseFileAction(parts);
			
		}).collect(Collectors.toList())).build();
		result.add(changeInfo);
		gitlog.addChild("commit").
			addChild("revision").setTextContent(hash).getParent().
			addChild("author").setTextContent(author).getParent().
			addChild("date").setTextContent(date).getParent().
			addChild("comment").setTextContent(comment).getParent().
			addChild("files").addChildren(files, (f,n) -> {
				String[] parts = f.split("\t");
				n.addChild("file").
					addAttribute("action", parts[0]).
					setTextContent(parts[1]);
				if (parts.length == 3) {
					n.addChild("file").
						addAttribute("action", parts[0]).
						setTextContent(parts[2]);
				}
			});
			
			
		commits.add(h);
		return result;
	}

	private FileChangeInfo parseFileAction(String[] parts) {
		ChangeStatus status;
		switch (parts[0]) {
			case "A": status = ChangeStatus.ADDED; break;
			case "M": status = ChangeStatus.MODIFIED; break;
			case "D": status = ChangeStatus.DELETED; break;
			default: status = ChangeStatus.MOVED; break;
		}
		try {
			return FileChangeInfo.builder().
					withLocation(Paths.get(parts.length == 2 ? parts[1] : parts[2])).
					withOriginalLocation(Optional.ofNullable(parts.length == 3 ? Paths.get(parts[1] ): null)).
					withStatus(status).
					build();
		} catch (Exception e) {
			throw new RuntimeException("Could not create FileChangeInfo for " + Lists.asList(parts), e);
		}
	}
}