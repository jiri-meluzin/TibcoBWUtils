grammar GITLogGrammer;

createLog :	(commitRule)+;
commitRule:	
	commitLine 
	mergeLine? 
	authorLine 
	dateLine 
	NEWLINE 
	(comments 
	(NEWLINE 
	files?)? 
	NEWLINE?)+
    ;
files: (file NEWLINE?)+;
file: FILE;
comments: (comment NEWLINE)+;
comment: COMMENT | EMPTY_COMMENT;
dateLine: DATE NEWLINE;
mergeLine: MERGE NEWLINE;
commitLine: COMMIT HASH branchInfo? NEWLINE;
authorLine: AUTHOR NEWLINE;
branchInfo: LBRACKET BRANCHNAME (ARROW BRANCHNAME)? RBRACKET;

LBRACKET: ' (';
RBRACKET: ')';
ARROW: ' -> ';
COMMIT: 'commit ';
DATE: 'Date: '  ~('>'|'\r'|'\n')* ('+'|'-') ('0'..'9') ('0'..'9') ('0'..'9') ('0'..'9');
MERGE: 'Merge: '  HASH ' ' HASH;
AUTHOR: 'Author: ' ~('>'|'\r'|'\n')* '>';
HASH: (('0'..'9')|('a'..'f'))+;
FILE: FILE_ACTION '\t' FILE_NAME;
fragment FILE_NAME:  ~('\r'|'\n')+;
FILE_ACTION: ('A'|'M'|'D'|('R' '0'..'9'? '0'..'9' '0'..'9'));
COMMENT: '    ' ~('\r'|'\n')+;
EMPTY_COMMENT: '    ';
BRANCHNAME: ('a'..'z'|'A'..'Z'|'/'|'_'|'-'|'0'..'9')+;

NEWLINE : '\r' ? '\n';
