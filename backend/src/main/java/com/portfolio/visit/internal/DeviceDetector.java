package com.portfolio.visit.internal;

import com.portfolio.visit.DeviceType;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import ua_parser.Client;
import ua_parser.Parser;

/**
 * Classifies a User-Agent: robots (uap-java "Spider" family plus common crawler keywords), then tablets,
 * phones and desktops.
 */
@Component
class DeviceDetector
{
    private static final Pattern BOT = Pattern.compile(
        "bot|crawl|spider|slurp|scrap|fetch|headless|lighthouse|pingdom|monitor|curl|wget|python-requests|"
            + "httpclient|java/|go-http|facebookexternalhit|preview|embedly|uptime");
    private static final Pattern TABLET = Pattern.compile("ipad|tablet|kindle|silk|playbook|sm-t|nexus (7|9|10)");
    private static final Pattern MOBILE =
        Pattern.compile("mobi|iphone|ipod|android|windows phone|blackberry|opera mini");

    private final Parser parser = new Parser();

    DeviceType detect(String userAgent)
    {
        if (userAgent == null || userAgent.isBlank())
        {
            return DeviceType.BOT;
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        Client client = parser.parse(userAgent);
        if ("Spider".equals(client.device.family) || BOT.matcher(ua).find())
        {
            return DeviceType.BOT;
        }
        if (TABLET.matcher(ua).find() || (ua.contains("android") && !ua.contains("mobile")))
        {
            return DeviceType.TABLET;
        }
        if (MOBILE.matcher(ua).find())
        {
            return DeviceType.MOBILE;
        }
        return DeviceType.DESKTOP;
    }
}
