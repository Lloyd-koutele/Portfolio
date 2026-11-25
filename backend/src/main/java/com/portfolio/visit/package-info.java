/**
 * Visit tracking: one row per page viewed by a visitor, IP stored according to the site's IP mode, devices
 * and robots detected from the User-Agent, administrator excluded, rows purged after 13 months. CV downloads
 * are logged the same way ({@link com.portfolio.visit.DownloadLog}).
 */
@ApplicationModule(displayName = "Visits")
package com.portfolio.visit;

import org.springframework.modulith.ApplicationModule;
