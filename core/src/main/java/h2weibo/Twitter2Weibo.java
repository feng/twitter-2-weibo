/*
 * (The MIT License)
 *
 * Copyright (c) 2011 Rakuraku Jyo <jyo.rakuraku@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the 'Software'), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to
 * do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 */

package h2weibo;

import com.sun.tools.corba.se.idl.toJavaPortable.Helper;
import h2weibo.model.DBHelper;
import h2weibo.model.T2WUser;
import h2weibo.utils.StatusImageExtractor;
import h2weibo.utils.filters.*;
import org.apache.log4j.Logger;
import twitter4j.*;
import twitter4j.auth.AccessToken;
import weibo4j.Weibo;
import weibo4j.WeiboException;
import weibo4j.http.ImageItem;

import java.util.List;

/**
 * @author Rakuraku Jyo
 */
public class Twitter2Weibo {
    private static final Logger log = Logger.getLogger(Twitter2Weibo.class.getName());
    private Weibo weibo;
    private DBHelper helper;


    public Twitter2Weibo(DBHelper helper) {
        this.helper = helper;
        weibo = new Weibo();
    }

    public void syncTwitter(String userId) {
        T2WUser user = helper.findOneByUser(userId);
        
        weibo.setToken(user.getToken(), user.getTokenSecret());

        Twitter twitter = new TwitterFactory().getInstance();
        if (user.getTwitterToken() != null) {
            twitter.setOAuthAccessToken(new AccessToken(user.getTwitterToken(), user.getTwitterTokenSecret()));
            log.debug("Using OAuth for " + user.getUserId());
        }

        StatusFilters filters = new StatusFilters();
        filters.use(new URLStatusFilter()).use(new TagStatusFilter());

        if (user.isDropMentions()) {
            filters.use(new NoMentionFilter());
        } else {
            filters.use(new UserMappingFilter(helper));
        }

        if (!user.ready()) {
            log.debug("Skipping @" + user.getUserId() + " ...");
            return;
        }

        // gets Twitter instance with default credentials
        String screenName = user.getUserId();
        long latestId = user.getLatestId();

        log.info(String.format("Checking @%s's timeline, latest ID = %d.", userId, latestId));

        try {
            if (latestId == 0) {
                List<Status> statuses = twitter.getUserTimeline(screenName);
                if (statuses.size() > 0) {
                    user.setLatestId(statuses.get(0).getId()); // Record latestId, and sync next time
                }
                log.info(String.format("First time use for @%s. Set latest ID to %d.", userId, latestId));
            } else {
                Paging paging = new Paging(latestId);
                List<Status> statuses = twitter.getUserTimeline(screenName, paging);

                // sync from the oldest one
                for (int i = statuses.size() - 1; i >= 0; i--) {
                    Status status = statuses.get(i);

                    if (status.getId() < user.getLatestId()) continue; // safe keeper

                    log.info("@" + status.getUser().getScreenName() + " - " + status.getText());
                    try {
                        if (user.isDropRTAndReply() && status.isRetweet()) {
                            user.setLatestId(status.getId());
                            log.info("Skipped " + status.getText() + " because status is a retweet.");
                            continue;
                        }

                        String filtered = filters.filter(status.getText());
                        if (filtered == null) {
                            user.setLatestId(status.getId());
                            log.info("Skipped " + status.getText() + " because of the filter.");
                            continue;
                        }


                        if (!user.isNoImage()) {
                            StatusImageExtractor ex = new StatusImageExtractor();
                            byte[] image = ex.extract(status.getText());
                            if (image != null) {
                                user.setLatestId(status.getId());
                                weibo.uploadStatus(status.getText(), new ImageItem(image));
                                log.info("@" + status.getUser().getScreenName() + " - " + status.getText() + " sent with image.");
                                continue;
                            }
                        }

                        GeoLocation location = status.getGeoLocation();
                        if (user.isWithGeo() && location != null) {
                            weibo.updateStatus(filtered, location.getLatitude(), location.getLongitude());
                            log.info("@" + status.getUser().getScreenName() + " - " + status.getText() + " sent with geo locations.");
                        } else {
                            weibo.updateStatus(filtered);
                            log.info("@" + status.getUser().getScreenName() + " - " + status.getText() + " sent.");
                        }
                    } catch (WeiboException e) {
                        if (e.getStatusCode() != 400) { // resending same tweet
                            log.warn("Failed to update Weibo");
                            throw new RuntimeException(e);
                        }
                    }
                    user.setLatestId(status.getId());

                }
            }
            user.save();
        } catch (Exception e) {
            log.error("Failed to update.", e);
        }
    }
}
