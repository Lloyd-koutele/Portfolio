package com.portfolio.page.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Validates a page document before it is stored: known element types and props only, bounded numbers,
 * safe links (http, https, mailto), YouTube / Vimeo ids, at most 300 elements and 1 MB. The server is the source
 * of truth; anything not explicitly allowed is rejected.
 */
@Component
class PageContentValidator
{
    static final int SCHEMA_VERSION = 1;
    static final int MAX_ELEMENTS = 300;
    static final int MAX_BYTES = 1024 * 1024;
    /** System fonts, then the web fonts bundled with the front end (see shared/src/fonts.css). */
    static final Set<String> FONTS = Set.of("Inter", "Arial", "Helvetica", "Georgia", "Times New Roman",
        "Courier New", "Verdana", "Trebuchet MS", "Poppins", "Montserrat", "Roboto", "Open Sans",
        "Playfair Display", "Lora", "Merriweather", "Fira Code");

    private static final double MAX_COORDINATE = 10_000;
    private static final double MAX_SIZE = 20_000;
    private static final Pattern COLOR =
        Pattern.compile("#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})|transparent");
    private static final Pattern YOUTUBE_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern VIMEO_ID = Pattern.compile("[0-9]{6,12}");
    private static final Pattern SVG_PATH = Pattern.compile("[MmLlHhVvCcSsQqTtAaZz0-9eE.,+\\-\\s]*");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final Set<String> ELEMENT_KEYS = Set.of("id", "type", "x", "y", "width", "height", "rotation",
        "zIndex", "opacity", "locked", "props");
    private static final Set<String> FONT_WEIGHTS = Set.of("100", "200", "300", "400", "500", "600", "700", "800",
        "900");

    private final ObjectMapper objectMapper;

    PageContentValidator(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    /**
     * @return the validated document and the media it references
     * @throws InvalidPageContentException listing every problem found
     */
    ValidatedContent validate(JsonNode content)
    {
        Errors errors = new Errors();
        Set<UUID> media = new HashSet<>();
        Set<UUID> videos = new HashSet<>();
        if (content == null || !content.isObject())
        {
            errors.add("content", "must be an object");
            throw new InvalidPageContentException(errors.map);
        }
        String json;
        try
        {
            json = objectMapper.writeValueAsString(content);
        }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception)
        {
            errors.add("content", "is not valid JSON");
            throw new InvalidPageContentException(errors.map);
        }
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES)
        {
            errors.add("content", "exceeds 1 MB");
            throw new InvalidPageContentException(errors.map);
        }
        onlyKeys(content, Set.of("schemaVersion", "background", "elements"), "content", errors);
        JsonNode version = content.get("schemaVersion");
        if (version == null || !version.isInt() || version.intValue() != SCHEMA_VERSION)
        {
            errors.add("schemaVersion", "must be " + SCHEMA_VERSION);
        }
        background(content.get("background"), errors, media);
        JsonNode elements = content.get("elements");
        if (elements == null || !elements.isArray())
        {
            errors.add("elements", "must be an array");
        }
        else if (elements.size() > MAX_ELEMENTS)
        {
            errors.add("elements", "at most " + MAX_ELEMENTS + " elements");
        }
        else
        {
            Set<String> ids = new HashSet<>();
            for (int index = 0; index < elements.size(); index++)
            {
                element(elements.get(index), "elements[" + index + "]", ids, errors, media, videos);
            }
        }
        if (!errors.map.isEmpty())
        {
            throw new InvalidPageContentException(errors.map);
        }
        return new ValidatedContent(json, media, videos);
    }

    private void background(JsonNode node, Errors errors, Set<UUID> media)
    {
        String path = "background";
        if (node == null || !node.isObject())
        {
            errors.add(path, "must be an object");
            return;
        }
        String type = text(node, "type");
        if ("color".equals(type))
        {
            onlyKeys(node, Set.of("type", "value"), path, errors);
            color(node, "value", path, true, errors);
        }
        else if ("image".equals(type))
        {
            onlyKeys(node, Set.of("type", "mediaId", "fit", "value"), path, errors);
            mediaId(node, path, errors, media);
            oneOf(node, "fit", Set.of("cover", "contain"), path, false, errors);
            color(node, "value", path, false, errors);
        }
        else
        {
            errors.add(path + ".type", "must be color or image");
        }
    }

    private void element(JsonNode node, String path, Set<String> ids, Errors errors, Set<UUID> media,
        Set<UUID> videos)
    {
        if (node == null || !node.isObject())
        {
            errors.add(path, "must be an object");
            return;
        }
        onlyKeys(node, ELEMENT_KEYS, path, errors);
        String id = text(node, "id");
        if (!isUuid(id))
        {
            errors.add(path + ".id", "must be a UUID");
        }
        else if (!ids.add(id))
        {
            errors.add(path + ".id", "is duplicated");
        }
        number(node, "x", -MAX_COORDINATE, MAX_COORDINATE, path, true, errors);
        number(node, "y", -MAX_COORDINATE, MAX_COORDINATE, path, true, errors);
        number(node, "width", 1, MAX_SIZE, path, true, errors);
        number(node, "height", 1, MAX_SIZE, path, true, errors);
        number(node, "rotation", -360, 360, path, true, errors);
        integer(node, "zIndex", 0, 100_000, path, errors);
        number(node, "opacity", 0, 1, path, true, errors);
        bool(node, "locked", path, false, errors);
        JsonNode props = node.get("props");
        String propsPath = path + ".props";
        if (props == null || !props.isObject())
        {
            errors.add(propsPath, "must be an object");
            return;
        }
        String type = text(node, "type");
        switch (type == null ? "" : type)
        {
            case "text" -> textProps(props, propsPath, errors);
            case "image" -> imageProps(props, propsPath, errors, media);
            case "shape" -> shapeProps(props, propsPath, errors);
            case "drawing" -> drawingProps(props, propsPath, errors);
            case "video" -> videoProps(props, propsPath, errors, media, videos);
            case "button" -> buttonProps(props, propsPath, errors);
            case "toc" -> tocProps(props, propsPath, errors);
            default -> errors.add(path + ".type", "unknown element type");
        }
    }

    private void textProps(JsonNode props, String path, Errors errors)
    {
        onlyKeys(props, Set.of("text", "fontFamily", "fontSize", "fontWeight", "italic", "underline", "align",
            "color", "lineHeight", "href"), path, errors);
        string(props, "text", 0, 5000, path, true, errors);
        oneOf(props, "fontFamily", FONTS, path, true, errors);
        number(props, "fontSize", 4, 400, path, true, errors);
        JsonNode weight = props.get("fontWeight");
        if (weight == null || !weight.isInt() || !FONT_WEIGHTS.contains(weight.asText()))
        {
            errors.add(path + ".fontWeight", "must be 100..900 in steps of 100");
        }
        bool(props, "italic", path, true, errors);
        bool(props, "underline", path, true, errors);
        oneOf(props, "align", Set.of("left", "center", "right", "justify"), path, true, errors);
        color(props, "color", path, true, errors);
        number(props, "lineHeight", 0.5, 4, path, true, errors);
        link(props, path, false, errors);
    }

    private void imageProps(JsonNode props, String path, Errors errors, Set<UUID> media)
    {
        onlyKeys(props, Set.of("mediaId", "alt", "fit", "borderRadius", "href"), path, errors);
        mediaId(props, path, errors, media);
        string(props, "alt", 1, 500, path, true, errors);
        oneOf(props, "fit", Set.of("cover", "contain"), path, true, errors);
        number(props, "borderRadius", 0, 1000, path, true, errors);
        link(props, path, false, errors);
    }

    private void shapeProps(JsonNode props, String path, Errors errors)
    {
        onlyKeys(props, Set.of("shape", "fill", "stroke", "strokeWidth", "borderRadius"), path, errors);
        oneOf(props, "shape", Set.of("rect", "ellipse", "triangle", "line"), path, true, errors);
        color(props, "fill", path, true, errors);
        color(props, "stroke", path, true, errors);
        number(props, "strokeWidth", 0, 100, path, true, errors);
        number(props, "borderRadius", 0, 1000, path, true, errors);
    }

    private void drawingProps(JsonNode props, String path, Errors errors)
    {
        onlyKeys(props, Set.of("path", "viewBox", "stroke", "strokeWidth", "fill"), path, errors);
        string(props, "path", 1, 200_000, path, true, errors);
        String svgPath = text(props, "path");
        if (svgPath != null && !SVG_PATH.matcher(svgPath).matches())
        {
            errors.add(path + ".path", "contains invalid characters");
        }
        String viewBox = text(props, "viewBox");
        String[] parts = viewBox == null ? new String[0] : viewBox.trim().split("\\s+");
        boolean validViewBox = parts.length == 4;
        for (String part : parts)
        {
            validViewBox &= NUMBER.matcher(part).matches() && part.length() < 16;
        }
        if (!validViewBox)
        {
            errors.add(path + ".viewBox", "must be four numbers");
        }
        color(props, "stroke", path, true, errors);
        number(props, "strokeWidth", 0, 200, path, true, errors);
        color(props, "fill", path, true, errors);
    }

    private void videoProps(JsonNode props, String path, Errors errors, Set<UUID> media, Set<UUID> videos)
    {
        String provider = text(props, "provider");
        if ("file".equals(provider))
        {
            onlyKeys(props, Set.of("provider", "mediaId"), path, errors);
            String id = text(props, "mediaId");
            mediaId(props, path, errors, media);
            if (isUuid(id))
            {
                videos.add(UUID.fromString(id));
            }
            return;
        }
        onlyKeys(props, Set.of("provider", "videoId"), path, errors);
        String videoId = text(props, "videoId");
        if ("youtube".equals(provider))
        {
            if (videoId == null || !YOUTUBE_ID.matcher(videoId).matches())
            {
                errors.add(path + ".videoId", "is not a valid YouTube id");
            }
        }
        else if ("vimeo".equals(provider))
        {
            if (videoId == null || !VIMEO_ID.matcher(videoId).matches())
            {
                errors.add(path + ".videoId", "is not a valid Vimeo id");
            }
        }
        else
        {
            errors.add(path + ".provider", "must be youtube, vimeo or file");
        }
    }

    private void buttonProps(JsonNode props, String path, Errors errors)
    {
        onlyKeys(props, Set.of("label", "href", "fill", "color", "borderRadius"), path, errors);
        string(props, "label", 1, 200, path, true, errors);
        link(props, path, true, errors);
        color(props, "fill", path, true, errors);
        color(props, "color", path, true, errors);
        number(props, "borderRadius", 0, 1000, path, true, errors);
    }

    /** Automatic table of contents: only its style is stored, the entries are computed from the book. */
    private void tocProps(JsonNode props, String path, Errors errors)
    {
        onlyKeys(props, Set.of("fontFamily", "fontSize", "fontWeight", "color", "accentColor", "lineHeight",
            "leaders"), path, errors);
        oneOf(props, "fontFamily", FONTS, path, true, errors);
        number(props, "fontSize", 8, 120, path, true, errors);
        JsonNode weight = props.get("fontWeight");
        if (weight == null || !weight.isInt() || !FONT_WEIGHTS.contains(weight.asText()))
        {
            errors.add(path + ".fontWeight", "must be 100..900 in steps of 100");
        }
        color(props, "color", path, true, errors);
        color(props, "accentColor", path, true, errors);
        number(props, "lineHeight", 1, 4, path, true, errors);
        bool(props, "leaders", path, true, errors);
    }

    // ---------------------------------------------------------------- primitives

    /** Accepts http, https and mailto links only. */
    static boolean isSafeLink(String value)
    {
        if (value == null || value.isBlank() || value.length() > 2000
            || value.chars().anyMatch(Character::isISOControl))
        {
            return false;
        }
        try
        {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            if (scheme == null)
            {
                return false;
            }
            return switch (scheme.toLowerCase(java.util.Locale.ROOT))
            {
                case "http", "https" -> uri.getHost() != null && !uri.getHost().isBlank();
                case "mailto" -> uri.getSchemeSpecificPart() != null && uri.getSchemeSpecificPart().contains("@");
                default -> false;
            };
        }
        catch (URISyntaxException exception)
        {
            return false;
        }
    }

    private static void link(JsonNode node, String path, boolean required, Errors errors)
    {
        JsonNode value = node.get("href");
        if (value == null || (!required && value.isTextual() && value.asText().isEmpty()))
        {
            if (required)
            {
                errors.add(path + ".href", "is required");
            }
            return;
        }
        if (!value.isTextual() || !isSafeLink(value.asText()))
        {
            errors.add(path + ".href", "must be an http, https or mailto link");
        }
    }

    private static void mediaId(JsonNode node, String path, Errors errors, Set<UUID> media)
    {
        String value = text(node, "mediaId");
        if (!isUuid(value))
        {
            errors.add(path + ".mediaId", "must be a UUID");
            return;
        }
        media.add(UUID.fromString(value));
    }

    private static void onlyKeys(JsonNode node, Set<String> allowed, String path, Errors errors)
    {
        Iterator<String> names = node.fieldNames();
        while (names.hasNext())
        {
            String name = names.next();
            if (!allowed.contains(name))
            {
                errors.add(path + "." + name, "is not allowed");
            }
        }
    }

    private static void number(JsonNode node, String key, double min, double max, String path, boolean required,
        Errors errors)
    {
        JsonNode value = node.get(key);
        if (value == null)
        {
            if (required)
            {
                errors.add(path + "." + key, "is required");
            }
            return;
        }
        if (!value.isNumber() || !Double.isFinite(value.doubleValue()) || value.doubleValue() < min
            || value.doubleValue() > max)
        {
            errors.add(path + "." + key, "must be a number between " + min + " and " + max);
        }
    }

    private static void integer(JsonNode node, String key, int min, int max, String path, Errors errors)
    {
        JsonNode value = node.get(key);
        if (value == null || !value.canConvertToInt() || !value.isIntegralNumber() || value.intValue() < min
            || value.intValue() > max)
        {
            errors.add(path + "." + key, "must be an integer between " + min + " and " + max);
        }
    }

    private static void bool(JsonNode node, String key, String path, boolean required, Errors errors)
    {
        JsonNode value = node.get(key);
        if (value == null ? required : !value.isBoolean())
        {
            errors.add(path + "." + key, "must be a boolean");
        }
    }

    private static void string(JsonNode node, String key, int min, int max, String path, boolean required,
        Errors errors)
    {
        JsonNode value = node.get(key);
        if (value == null)
        {
            if (required)
            {
                errors.add(path + "." + key, "is required");
            }
            return;
        }
        if (!value.isTextual() || value.asText().length() > max || value.asText().strip().length() < min)
        {
            errors.add(path + "." + key, "must be a text of " + min + " to " + max + " characters");
        }
    }

    private static void oneOf(JsonNode node, String key, Set<String> allowed, String path, boolean required,
        Errors errors)
    {
        JsonNode value = node.get(key);
        if (value == null)
        {
            if (required)
            {
                errors.add(path + "." + key, "is required");
            }
            return;
        }
        if (!value.isTextual() || !allowed.contains(value.asText()))
        {
            errors.add(path + "." + key, "must be one of " + allowed);
        }
    }

    private static void color(JsonNode node, String key, String path, boolean required, Errors errors)
    {
        JsonNode value = node.get(key);
        if (value == null)
        {
            if (required)
            {
                errors.add(path + "." + key, "is required");
            }
            return;
        }
        if (!value.isTextual() || !COLOR.matcher(value.asText()).matches())
        {
            errors.add(path + "." + key, "must be a hex color or transparent");
        }
    }

    private static String text(JsonNode node, String key)
    {
        JsonNode value = node.get(key);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private static boolean isUuid(String value)
    {
        if (value == null || value.length() != 36)
        {
            return false;
        }
        try
        {
            UUID.fromString(value);
            return true;
        }
        catch (IllegalArgumentException exception)
        {
            return false;
        }
    }

    /** Empty page used for new pages. */
    ObjectNode emptyContent()
    {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        ObjectNode background = root.putObject("background");
        background.put("type", "color");
        background.put("value", "#ffffff");
        root.putArray("elements");
        return root;
    }

    /**
     * @param mediaIds every referenced media
     * @param videoIds the ones used by video elements (they must be videos; all others must be images)
     */
    record ValidatedContent(String json, Set<UUID> mediaIds, Set<UUID> videoIds)
    {
    }

    private static final class Errors
    {
        private static final int MAX_ERRORS = 50;
        private final Map<String, String> map = new LinkedHashMap<>();

        void add(String path, String message)
        {
            if (map.size() < MAX_ERRORS)
            {
                map.putIfAbsent(path, message);
            }
        }
    }
}
