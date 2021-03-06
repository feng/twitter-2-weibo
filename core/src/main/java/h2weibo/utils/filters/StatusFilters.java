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

package h2weibo.utils.filters;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: jyo
 * Date: 11/06/12
 * Time: 13:29
 * To change this template use File | Settings | File Templates.
 */
public class StatusFilters {
    List<StatusFilter> filters = new ArrayList<StatusFilter>();

    public StatusFilters use(StatusFilter filter) {
        filters.add(filter);
        return this;
    }

    public StatusFilters remove(StatusFilter filter) {
        filters.remove(filter);
        return this;
    }

    public String filter(String input) {
        String output = input;
        for (StatusFilter filter : filters) {
            output = filter.filter(output);
        }
        return output;
    }
}
