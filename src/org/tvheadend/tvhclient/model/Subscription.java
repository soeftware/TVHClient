/*
 *  Copyright (C) 2011 John Törnblom
 *
 * This file is part of TVHGuide.
 *
 * TVHGuide is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TVHGuide is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with TVHGuide.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.tvheadend.tvhclient.model;

import java.util.ArrayList;
import java.util.List;

public class Subscription {

    public long id;
    public String status;
    public List<Stream> streams = new ArrayList<Stream>();

    public long packetCount;
    public long queSize;
    public long delay;
    public long droppedBFrames;
    public long droppedIFrames;
    public long droppedPFrames;
    public long graceTimeout;
    public String feStatus;
    public int feSNR;
    public int feSignal;
    public int feBER;
    public int feUNC;
    public SourceInfo sourceInfo;
}
