/*--------------------------------------------------------------*
  Copyright (C) 2006-2015 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  'License' for details on this and other legal matters.
*--------------------------------------------------------------*/

package org.omnetpp.common.editor.text;

import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;
import org.omnetpp.common.util.DisplayUtils;

/**
 * Data for syntax highlighting
 *
 * @author rhornig, andras
 */
public class SyntaxHighlightHelper {
    // word lists for syntax highlighting
    public final static String[] highlightPrivateDocTodo = Keywords.DOC_TODO;
    public final static String[] highlightDocTags = Keywords.DOC_TAGS;
    public final static String[] highlightDocKeywords = Keywords.DOC_KEYWORDS;
    public final static String[] highlightNedTypes = Keywords.NED_TYPE_KEYWORDS;
    public final static String[] highlightNedSpecialKeywords = Keywords.NED_SPECIAL_KEYWORDS;
    public final static String[] highlightNedKeywords = Keywords.concat(Keywords.NED_NONEXPR_KEYWORDS, Keywords.NED_EXPR_KEYWORDS);
    public final static String[] highlightNedExprKeywords = Keywords.NED_EXPR_KEYWORDS;
    public final static String[] highlightNedFunctions = Keywords.NED_FUNCTIONS;
    public final static String[] highlightMsgTypes = Keywords.MSG_TYPE_KEYWORDS;
    public final static String[] highlightMsgKeywords = Keywords.concat(Keywords.MSG_KEYWORDS, Keywords.MSG_SECTION_KEYWORDS);
    public final static String[] highlightConstants = Keywords.BOOL_CONSTANTS;

    // tokens for syntax highlighting (in light theme)
    public static IToken docDefaultToken = new Token(new TextAttribute(getColor(SWT.COLOR_DARK_GRAY), null, SWT.ITALIC));
    public static IToken docKeywordToken = new Token(new TextAttribute(getColor(SWT.COLOR_DARK_BLUE)));
    public static IToken docTagToken = new Token(new TextAttribute(getColor(SWT.COLOR_DARK_RED)));
    public static IToken docPrivateDefaultToken = new Token(new TextAttribute(getColor(SWT.COLOR_BLUE), null, SWT.ITALIC));
    public static IToken docPrivateTodoToken = new Token(new TextAttribute(getColor(SWT.COLOR_DARK_BLUE)));
    public static IToken codeDefaultToken = new Token(new TextAttribute(getColor(SWT.COLOR_WIDGET_FOREGROUND)));
    public static IToken codeKeywordToken = new Token(new TextAttribute(getColor(SWT.COLOR_DARK_RED), null, SWT.BOLD));
    public static IToken codeFunctionToken = new Token(new TextAttribute(getColor(SWT.COLOR_DARK_MAGENTA)));
    public static IToken codeTypeToken = new Token(new TextAttribute(getColor(SWT.COLOR_DARK_BLUE), null, SWT.BOLD));
    public static IToken codeIdentifierToken = new Token(new TextAttribute(getColor(SWT.COLOR_WIDGET_FOREGROUND)));
    public static IToken codePropertyToken = new Token(new TextAttribute(getColor(SWT.COLOR_WIDGET_FOREGROUND), null, SWT.BOLD));
    public static IToken codeStringToken = new Token(new TextAttribute(getColor(SWT.COLOR_DARK_GREEN)));
    public static IToken codeNumberToken = new Token(new TextAttribute(getColor(SWT.COLOR_DARK_GREEN)));
    
    static {
    	if (DisplayUtils.isDarkTheme()) {
    		docDefaultToken = new Token(new TextAttribute(getColor(SWT.COLOR_DARK_GRAY), null, SWT.ITALIC));
    		docKeywordToken = new Token(new TextAttribute(getColor(SWT.COLOR_DARK_YELLOW)));
    		docTagToken = new Token(new TextAttribute(new Color(80, 80, 80)));
    		docPrivateDefaultToken = new Token(new TextAttribute(getColor(SWT.COLOR_BLUE), null, SWT.ITALIC));
    		docPrivateTodoToken = new Token(new TextAttribute(getColor(SWT.COLOR_DARK_BLUE)));
    		codeDefaultToken = new Token(new TextAttribute(getColor(SWT.COLOR_WIDGET_FOREGROUND)));
    		codeKeywordToken = new Token(new TextAttribute(new Color(128, 128, 64), null, SWT.BOLD));
    		codeFunctionToken = new Token(new TextAttribute(new Color(128, 128, 255)));
    		codeTypeToken = new Token(new TextAttribute(getColor(SWT.COLOR_DARK_CYAN), null, SWT.BOLD));
    		codeIdentifierToken = new Token(new TextAttribute(getColor(SWT.COLOR_WIDGET_FOREGROUND)));
    		codePropertyToken = new Token(new TextAttribute(new Color(192, 192, 128), null, SWT.BOLD));
    		codeStringToken = new Token(new TextAttribute(new Color(128, 220, 128)));
    		codeNumberToken = new Token(new TextAttribute(new Color(128, 220, 128)));
    	}
    }

    /**
     * Convenience method, to return a system default color. Color constants come from SWT class e.g. SWT.COLOR_RED
     */
    private static Color getColor(final int swtColor) {
        Color[] holder = new Color[1];
        DisplayUtils.runNowOrSyncInUIThread(() -> { holder[0] = Display.getDefault().getSystemColor(swtColor); });
        return holder[0];
    }

    /**
     * Detector for normal NED keywords (may start with letter, @ or _ and contain letter number or _)
     */
    public static class NedWordDetector implements IWordDetector {
        public boolean isWordStart(char character) {
            return Character.isLetter(character) || character == '_' || character == '@';
        }

        public boolean isWordPart(char character) {
            return Character.isLetterOrDigit(character) || character == '_';
        }
    }

    /**
     * Detector for a NED qualified type names (a sequence of identifiers, separated by dots)
     */
    public static class NedDottedWordDetector implements IWordDetector {
        public boolean isWordStart(char character) {
            return Character.isLetter(character) || character == '_' || character == '@';
        }

        public boolean isWordPart(char character) {
            return Character.isLetterOrDigit(character) || character == '_' || character == '.';
        }
    }

    /**
     * Detector for extreme NED keywords (in: out: --> <-- .. ...) where the keyword may contain special chars
     */
    public static class NedSpecialWordDetector implements IWordDetector {
        public boolean isWordStart(char c) {
            return Character.isLetter(c) || c == '-' || c == '<' || c == '>' || c == '.';
        }

        public boolean isWordPart(char c) {
            return isWordStart(c) || c == ':';
        }
    }

    /**
     * Detects keywords that are starting with @ and continuing with letters only.
     */
    public static class NedAtWordDetector implements IWordDetector {
        public boolean isWordStart(char c) {
            return (c == '@');
        }

        public boolean isWordPart(char c) {
            return Character.isLetter(c);
        }
    }

    /**
     * Detects keywords that look like an XML tag.
     */
    public static class NedDocTagDetector implements IWordDetector {
        public boolean isWordStart(char c) {
            return (c == '<');
        }

        public boolean isWordPart(char c) {
            return Character.isLetter(c) || c == '/' || c == '>';
        }
    }

    /**
     * Detects tags in display strings.
     */
    public static class NedDisplayStringTagDetector implements IWordDetector {
        public boolean isWordStart(char c) {
            return Character.isLetter(c);
        }

        public boolean isWordPart(char c) {
            return c != ';' && c != '"';
        }
    }

    /**
     * Detects image names in display strings.
     */
    public static class NedDisplayStringImageNameDetector implements IWordDetector {
        public boolean isWordStart(char character) {
            return Character.isLetter(character);
        }

        public boolean isWordPart(char character) {
            return Character.isLetterOrDigit(character) || character == '/';
        }
    }

    /**
     * Detects tags in a property.
     */
    public static class NedPropertyTagDetector implements IWordDetector {
        public boolean isWordStart(char c) {
            return Character.isLetter(c);
        }

        public boolean isWordPart(char c) {
            return c != ';' && c != '(' && c != ')' && c != ' ';
        }
    }

    /**
     * Detects values in a property.
     */
    public static class NedPropertyTagValueDetector implements IWordDetector {
        public boolean isWordStart(char c) {
            return Character.isLetter(c);
        }

        public boolean isWordPart(char c) {
            return c != ';' && c != '(' && c != ')' && c != ' ' && c != '=' && c != ',';
        }
    }
}
