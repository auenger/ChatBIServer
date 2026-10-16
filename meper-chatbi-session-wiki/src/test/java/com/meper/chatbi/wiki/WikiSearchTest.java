package com.meper.chatbi.wiki;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WikiSearchTest {
 @Test void patternsDoNotExecuteRegexOrEscapeDirectories() {
  assertTrue(WikiSearch.glob("/schemas/*/tables/*.md").matcher("/schemas/sales/tables/orders.md").matches());
  assertFalse(WikiSearch.glob("/schemas/*/tables/*.md").matcher("/schemas/a/b/tables/orders.md").matches());
  assertTrue(WikiSearch.glob("/**/orders.md").matcher("/orders.md").matches());
  assertFalse(WikiSearch.glob("/(orders|users).md").matcher("/orders.md").matches());
  assertThrows(IllegalArgumentException.class,()->WikiSearch.glob("/../secret"));
  assertThrows(IllegalArgumentException.class,()->WikiSearch.glob("relative/path"));
 }
 @Test void unknownSubjectsAreDenied() {
  var access=new WikiAccess("admin");assertThrows(WikiAccess.Denied.class,()->access.check("other",1));
  assertThrows(WikiAccess.Denied.class,()->access.check(null,1));
 }
}
