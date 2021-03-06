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

import org.apache.commons.pool.impl.GenericObjectPool;
import org.apache.log4j.Logger;
import redis.clients.jedis.JedisPool;
import weibo4j.org.json.JSONException;
import weibo4j.org.json.JSONObject;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

/**
 * @author Rakuraku Jyo
 */
public class InitServlet extends HttpServlet {
    private static final Logger log = Logger.getLogger(InitServlet.class.getName());
    public static final String CONTEXT_JEDIS_POOL = "JEDIS_POOL";

    public JedisPool createJedisPool() {
        JedisPool jedisPool;

        GenericObjectPool.Config config = new GenericObjectPool.Config();
        config.testOnBorrow = true;
        config.maxActive = 25;
        config.maxIdle = 5;
        config.minIdle = 1;

        log.debug("Jedis pool created.");

        try {
            String services = System.getenv("VCAP_SERVICES");

            if (services != null) {
                JSONObject obj = new JSONObject(services);
                obj = obj.getJSONArray("redis-2.2").getJSONObject(0).getJSONObject("credentials");

                String hostname = obj.getString("hostname");
                int port = obj.getInt("port");
                String password = obj.getString("password");

                jedisPool = new JedisPool(config, hostname, port, 0, password);
            } else {
                jedisPool = new JedisPool(config, "localhost");
                log.info("Using localhost Redis server");
            }
            return jedisPool;
        } catch (JSONException e) {
            log.error("Failed to init", e);
            return null;
        }
    }

    public JedisPool getPool(ServletContext context) {
        return (JedisPool) context.getAttribute(CONTEXT_JEDIS_POOL);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        // Key for Weibo App
        System.setProperty("weibo4j.oauth.consumerKey", "2440858351");
        System.setProperty("weibo4j.oauth.consumerSecret", "1faf4ed7b4af302907e25429a0b88dfc");

        System.setProperty("weibo4j.debug", "false");

        // Key for Twitter App
        System.setProperty("twitter4j.oauth.consumerKey", "Scwn2HbdT7v3yOEjkAQrfQ");
        System.setProperty("twitter4j.oauth.consumerSecret", "QIz4dbgb5ABzNMjfP1Sb0YdwKTY2oKQwhLoehk0ug");

        log.info("System initialized.");

        getServletContext().setAttribute(CONTEXT_JEDIS_POOL, createJedisPool());
    }

    @Override
    public void destroy() {
        JedisPool pool = getPool(getServletContext());
        pool.destroy();
    }
}
