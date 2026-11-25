package com.portfolio.page.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PageContentValidatorTest
{
    private final ObjectMapper json = new ObjectMapper();
    private final PageContentValidator validator = new PageContentValidator(json);

    @Test
    void acceptsTheEmptyPage()
    {
        assertThat(validator.validate(validator.emptyContent()).mediaIds()).isEmpty();
    }

    @Test
    void safeLinksOnly()
    {
        assertThat(PageContentValidator.isSafeLink("https://example.com/a?b=c")).isTrue();
        assertThat(PageContentValidator.isSafeLink("http://example.com")).isTrue();
        assertThat(PageContentValidator.isSafeLink("mailto:moi@example.com")).isTrue();
        assertThat(PageContentValidator.isSafeLink("javascript:alert(1)")).isFalse();
        assertThat(PageContentValidator.isSafeLink("JaVaScRiPt:alert(1)")).isFalse();
        assertThat(PageContentValidator.isSafeLink("data:text/html,<script>")).isFalse();
        assertThat(PageContentValidator.isSafeLink("//example.com")).isFalse();
        assertThat(PageContentValidator.isSafeLink("https:///nohost")).isFalse();
        assertThat(PageContentValidator.isSafeLink("mailto:nobody")).isFalse();
    }

    @Test
    void rejectsWrongSchemaVersionAndUnknownKeys() throws Exception
    {
        String content = "{\"schemaVersion\":2,\"background\":{\"type\":\"color\",\"value\":\"#fff\"},"
            + "\"elements\":[],\"script\":\"x\"}";

        assertThatThrownBy(() -> validator.validate(json.readTree(content)))
            .isInstanceOf(InvalidPageContentException.class)
            .satisfies(error -> assertThat(((InvalidPageContentException) error).properties().toString())
                .contains("schemaVersion").contains("content.script"));
    }

    @Test
    void rejectsSvgPathInjection() throws Exception
    {
        String content = "{\"schemaVersion\":1,\"background\":{\"type\":\"color\",\"value\":\"#fff\"},"
            + "\"elements\":[{\"id\":\"8a7a1c5e-2b7c-4f7e-9d2a-0d6c1b2a3f4e\",\"type\":\"drawing\",\"x\":0,\"y\":0,"
            + "\"width\":10,\"height\":10,\"rotation\":0,\"zIndex\":1,\"opacity\":1,\"locked\":false,"
            + "\"props\":{\"path\":\"M0 0\\\"/><script>\",\"viewBox\":\"0 0 10 10\",\"stroke\":\"#000\","
            + "\"strokeWidth\":1,\"fill\":\"transparent\"}}]}";

        assertThatThrownBy(() -> validator.validate(json.readTree(content)))
            .isInstanceOf(InvalidPageContentException.class);
    }

    @Test
    void rejectsOutOfBoundsNumbersAndDuplicateIds() throws Exception
    {
        String element = "{\"id\":\"8a7a1c5e-2b7c-4f7e-9d2a-0d6c1b2a3f4e\",\"type\":\"shape\",\"x\":99999,\"y\":0,"
            + "\"width\":10,\"height\":10,\"rotation\":0,\"zIndex\":1,\"opacity\":2,\"locked\":false,"
            + "\"props\":{\"shape\":\"rect\",\"fill\":\"#000\",\"stroke\":\"transparent\",\"strokeWidth\":0,"
            + "\"borderRadius\":0}}";
        String content = "{\"schemaVersion\":1,\"background\":{\"type\":\"color\",\"value\":\"#fff\"},"
            + "\"elements\":[" + element + "," + element + "]}";

        assertThatThrownBy(() -> validator.validate(json.readTree(content)))
            .satisfies(error -> assertThat(((InvalidPageContentException) error).properties().toString())
                .contains("elements[0].x").contains("elements[0].opacity").contains("elements[1].id"));
    }

    @Test
    void acceptsAnImageBackgroundAndReportsItsMedia() throws Exception
    {
        String content = "{\"schemaVersion\":1,\"background\":{\"type\":\"image\","
            + "\"mediaId\":\"8a7a1c5e-2b7c-4f7e-9d2a-0d6c1b2a3f4e\",\"fit\":\"cover\",\"value\":\"#ffffff\"},"
            + "\"elements\":[]}";

        assertThat(validator.validate(json.readTree(content)).mediaIds()).hasSize(1);
    }

    @Test
    void rejectsBrokenBackgroundsAndNonObjects() throws Exception
    {
        assertInvalid("{\"schemaVersion\":1,\"background\":{\"type\":\"gradient\"},\"elements\":[]}",
            "background.type");
        assertInvalid("{\"schemaVersion\":1,\"background\":\"red\",\"elements\":[\"x\"]}", "background",
            "elements[0]");
        assertInvalid("{\"schemaVersion\":1,\"background\":{\"type\":\"image\",\"mediaId\":\"nope\"},"
            + "\"elements\":{}}", "background.mediaId", "elements");
        assertThatThrownBy(() -> validator.validate(json.readTree("[]")))
            .isInstanceOf(InvalidPageContentException.class);
    }

    @Test
    void checksTypedPropsOfEveryElement() throws Exception
    {
        String text = element("text", "{\"text\":5,\"fontFamily\":\"Comic Sans\",\"fontSize\":2,"
            + "\"fontWeight\":450,\"italic\":\"no\",\"underline\":false,\"align\":\"middle\",\"color\":\"red\","
            + "\"lineHeight\":9,\"href\":\"ftp://x\"}");
        String image = element("image", "{\"mediaId\":\"8a7a1c5e-2b7c-4f7e-9d2a-0d6c1b2a3f4e\",\"alt\":\" \","
            + "\"fit\":\"fill\",\"borderRadius\":0}");
        String video = element("video", "{\"provider\":\"vimeo\",\"videoId\":\"abc\"}");
        String other = element("video", "{\"provider\":\"dailymotion\",\"videoId\":\"abc\"}");
        String drawing = element("drawing", "{\"path\":\"M 0 0\",\"viewBox\":\"0 0 10\",\"stroke\":\"#000\","
            + "\"strokeWidth\":1,\"fill\":\"#000\"}");
        String noProps = element("shape", "null");

        assertInvalid("{\"schemaVersion\":1,\"background\":{\"type\":\"color\",\"value\":\"#fff\"},\"elements\":["
            + String.join(",", text, image, video, other, drawing, noProps) + "]}",
            "elements[0].props.text", "elements[0].props.fontFamily", "elements[0].props.fontSize",
            "elements[0].props.fontWeight", "elements[0].props.italic", "elements[0].props.align",
            "elements[0].props.color", "elements[0].props.lineHeight", "elements[0].props.href",
            "elements[1].props.alt", "elements[1].props.fit", "elements[2].props.videoId",
            "elements[3].props.provider", "elements[4].props.viewBox", "elements[5].props");
    }

    private static int counter;

    private static String element(String type, String props)
    {
        counter++;
        String id = String.format("8a7a1c5e-2b7c-4f7e-9d2a-%012d", counter);
        return "{\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"x\":0,\"y\":0,\"width\":10,\"height\":10,"
            + "\"rotation\":0,\"zIndex\":1,\"opacity\":1,\"locked\":false,\"props\":" + props + "}";
    }

    private void assertInvalid(String content, String... paths) throws Exception
    {
        assertThatThrownBy(() -> validator.validate(json.readTree(content)))
            .isInstanceOf(InvalidPageContentException.class)
            .satisfies(error ->
            {
                String errors = ((InvalidPageContentException) error).properties().toString();
                for (String path : paths)
                {
                    assertThat(errors).contains(path + "=");
                }
            });
    }

    @Test
    void acceptsTheTableOfContentsBlockAndChecksItsStyle() throws Exception
    {
        String good = element("toc", "{\"fontFamily\":\"Inter\",\"fontSize\":22,\"fontWeight\":600,"
            + "\"color\":\"#111111\",\"accentColor\":\"#ee7d1f\",\"lineHeight\":2,\"leaders\":true}");
        validator.validate(json.readTree("{\"schemaVersion\":1,\"background\":{\"type\":\"color\","
            + "\"value\":\"#fff\"},\"elements\":[" + good + "]}"));

        String bad = element("toc", "{\"fontFamily\":\"Inter\",\"fontSize\":500,\"fontWeight\":600,"
            + "\"color\":\"#111111\",\"accentColor\":\"red\",\"lineHeight\":2,\"leaders\":true,\"entries\":[]}");
        assertInvalid("{\"schemaVersion\":1,\"background\":{\"type\":\"color\",\"value\":\"#fff\"},"
            + "\"elements\":[" + bad + "]}", "elements[0].props.fontSize", "elements[0].props.accentColor",
            "elements[0].props.entries");
    }

    @Test
    void acceptsTheBundledWebFontsInBoldItalic() throws Exception
    {
        for (String font : new String[] {"Poppins", "Playfair Display", "Fira Code"})
        {
            String text = element("text", "{\"text\":\"Bonjour\",\"fontFamily\":\"" + font + "\",\"fontSize\":24,"
                + "\"fontWeight\":700,\"italic\":true,\"underline\":false,\"align\":\"left\","
                + "\"color\":\"#111111\",\"lineHeight\":1.2}");
            validator.validate(json.readTree("{\"schemaVersion\":1,\"background\":{\"type\":\"color\","
                + "\"value\":\"#fff\"},\"elements\":[" + text + "]}"));
        }
    }
}
