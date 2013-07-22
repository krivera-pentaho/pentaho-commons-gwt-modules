package org.pentaho.gwt.widgets.themes;

import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.pentaho.gwt.widgets.themes.ThemeSynchronizer.Style.DIFFERENCE;

public class ThemeSynchronizer {
  enum THEME {
    CRYSTAL, ONYX, SLATE
  }

  enum CHANGE {
    NEW, MISSING, SAME
  }

  public static void main(String[] args) throws Exception {
    synchronizeThemes();
  }

  public static void synchronizeThemes() throws Exception {

    Map<THEME, StyleSheet> themes = new HashMap<THEME, StyleSheet>();

    File root = new File("");

    // Create path to root of themes
    StringBuilder themesRoot = new StringBuilder();
    themesRoot
        .append(root.getAbsolutePath())
        .append("/src/")
        .append(
            ThemeSynchronizer.class.getCanonicalName().replace(".", "/")
                .replace(ThemeSynchronizer.class.getSimpleName(), "")).append("/public/themes/");

    // Create style maps
    for (THEME theme : THEME.values()) {

      String themeNameLC = theme.name().toLowerCase();

      // Create path to global*.css
      StringBuilder path = new StringBuilder(themesRoot);
      path.append(themeNameLC).append("/global").append(themeNameLC.substring(0, 1).toUpperCase())
          .append(themeNameLC.substring(1)).append(".css");

      File themeCss = new File(path.toString());
      themes.put(theme, new StyleSheet(themeCss));
    }

    THEME baseTheme = THEME.CRYSTAL;
    StyleSheet baseStyleSheet = themes.get(baseTheme);

    // Perform style comparisons
    for (THEME theme : THEME.values()) {
      if (baseTheme == theme) {
        continue;
      }

      StyleSheet styleSheet = themes.get(theme);
      Map<CHANGE, StyleSheet> comparisons = compare(baseStyleSheet, styleSheet);
      
      //      System.out.println("-----------------------------------");
      //      System.out.println(baseTheme + " >>>>> " + theme);
      //      System.out.println("-----------------------------------\n");
//
//      for (Entry<CHANGE, StyleSheet> entry : comparisons.entrySet()) {
//
//        System.out.println("************************************");
//        System.out.println(entry.getKey().toString());
//        System.out.println("************************************\n");
//
//        for (Style style : entry.getValue().styles) {
//          System.out.print(style);
//        }
//      }
      
      // Append common styles
      if (comparisons.containsKey(CHANGE.SAME)) {

        // Refresh common styles sheet by creating new file
        File commonStylesFile = new File(themesRoot + "commonStyles.css");
        StyleSheet commonStyles = parseStyleSheet(commonStylesFile);

        Map<CHANGE, StyleSheet> commonComparisons = compare(comparisons.get(CHANGE.SAME), commonStyles);

        // Append new changes to common styles
        if (commonComparisons.containsKey(CHANGE.NEW)){
          commonStyles.styles.addAll(commonComparisons.get(CHANGE.NEW).styles);
        }
        
        StringBuilder commonStylesOut = new StringBuilder();
        for (Style style : commonStyles.styles) {
          commonStylesOut.append(style);
        }
        
        FileWriter fw = new FileWriter(commonStylesFile);
        fw.write(commonStylesOut.toString());
        fw.close();
      }
    }
  }
  
  private static String getStringContent(File file) {
    try {
      return new Scanner(file).useDelimiter("\\Z").next();
    } catch (Exception e) {
      return "";
    }
  }

  private static StyleSheet parseStyleSheet(File file) {
    return parseStyleSheet(getStringContent(file));
  }

  private static StyleSheet parseStyleSheet(String fileContent) {
    Pattern cssDefinition = Pattern.compile("(.+?)\\{([\\S\\s]*?)\\}");
    Matcher m = cssDefinition.matcher(fileContent);

    Pattern cssBodyDefinitions = Pattern.compile("(.+)?:(.+?);");

    // Add theme key
    StyleSheet styleSheet = new StyleSheet();

    // Match on each style
    while (m.find()) {
      Style style = new Style(m.group(1));
      styleSheet.styles.add(style);

      // Match on css body elements
      Matcher m1 = cssBodyDefinitions.matcher(m.group(2));
      while (m1.find()) {
        style.elements.add(new StyleElement(m1.group(1), m1.group(2)));
      }
    }

    return styleSheet;
  }

  private static Map<CHANGE, StyleSheet> compare(StyleSheet s1, StyleSheet s2) {
    Map<CHANGE, StyleSheet> differences = new HashMap<CHANGE, StyleSheet>();

    compareStyleSets(s1.styles, s2.styles, DIFFERENCE.DEFINITION, CHANGE.NEW, differences);
    compareStyleSets(s2.styles, s1.styles, DIFFERENCE.DEFINITION, CHANGE.MISSING, differences);
    
    return differences;
  }

  private static void compareStyleSets(Set<Style> styles1, Set<Style> styles2, DIFFERENCE granularity,
      CHANGE direction, Map<CHANGE, StyleSheet> differences) {

    for (Style style1 : styles1) {

      boolean stylesPass = false;
      for (Style style2 : styles2) {

        // Test the type of difference
        DIFFERENCE difference = DIFFERENCE.values()[style1.compareTo(style2)];
        if (difference.ordinal() < granularity.ordinal()) {
          stylesPass = true;
        }

        // Add common styles
        if (difference == DIFFERENCE.NONE) {
          if (!differences.containsKey(CHANGE.SAME)) {
            differences.put(CHANGE.SAME, new StyleSheet());
          }

          differences.get(CHANGE.SAME).styles.add(style1);
        }
      }

      // Add the type of change that needs to be made
      if (!stylesPass) {
        if (!differences.containsKey(direction)){
          differences.put(direction, new StyleSheet());
        }
        
        differences.get(direction).styles.add(style1);
      }
    }

  }

  static class StyleSheet {
    public Set<Style> styles = new LinkedHashSet<Style>();

    public File file = null;
    public String content = new String();

    public StyleSheet() {
    }

    public StyleSheet(List<Style> styles) {
      this.styles.addAll(styles);
    }

    public StyleSheet(File file) {
      this.file = file;
      this.content = getStringContent(file);

      Pattern cssDefinition = Pattern.compile("(.+?)\\{([\\S\\s]*?)\\}");
      Matcher m = cssDefinition.matcher(this.content);

      Pattern cssBodyDefinitions = Pattern.compile("(.+)?:(.+?);");

      // Match on each style
      while (m.find()) {
        Style style = new Style(m.group(1));
        style.content = m.group();

        this.styles.add(style);

        // Match on css body elements
        Matcher m1 = cssBodyDefinitions.matcher(m.group(2));
        while (m1.find()) {
          style.elements.add(new StyleElement(m1.group(1), m1.group(2)));
        }
      }
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();

      for (Style style : this.styles) {
        sb.append(style);
      }

      return sb.toString();
    }
  }

  static class Style implements Comparable<Style> {
    public static enum DIFFERENCE {
      NONE, BODY_ELEMENT, DEFINITION
    }

    public String content = "";

    public String[] definitions;
    public Set<StyleElement> elements = new HashSet<>();

    public Style() {
    }

    public Style(String definitionsStr) {
      this.definitions = definitionsStr.split(",");
      int i = 0;
      for (String definition : this.definitions) {
        this.definitions[i] = definition.trim();
        i++;
      }
    }

    public Style(String definitionsStr, Set<StyleElement> elements) {
      this(definitionsStr);
      this.elements.addAll(elements);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < this.definitions.length; i++) {
        sb.append(this.definitions[i]);

        if (i < this.definitions.length - 1) {
          sb.append(", ");
        }
      }

      sb.append(" {");
      for (StyleElement styleElement : this.elements) {
        sb.append("\n").append(styleElement).append(";");
      }

      sb.append("\n}\n\n");

      return sb.toString();
    }

    @Override
    public int compareTo(Style style) {

      // Definitions do not equal
      for (String defintion1 : style.definitions) {
        boolean equals = false;
        for (String definition2 : this.definitions) {

          if (defintion1.equals(definition2)) {
            equals = true;
            break;
          }
        }

        if (!equals) {
          return DIFFERENCE.DEFINITION.ordinal();
        }
      }

      // Body elements not equal
      for (StyleElement element1 : style.elements) {
        boolean equals = false;
        for (StyleElement element2 : this.elements) {

          if (element1.equals(element2)) {
            equals = true;
            break;
          }
        }

        if (!equals) {
          return DIFFERENCE.BODY_ELEMENT.ordinal();
        }
      }

      // Equal
      return DIFFERENCE.NONE.ordinal();
    }
  }

  static class StyleElement {
    public String key, value;

    public StyleElement(String key, String value) {
      this.key = key.trim();
      this.value = value.trim();
    }

    @Override
    public String toString() {
      return "  " + key + " : " + value;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof StyleElement)) {
        return false;
      }

      StyleElement element = (StyleElement) obj;
      return element.key.equals(this.key) && element.value.equals(this.value);
    }
  }
}
