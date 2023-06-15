/**
 * Copyright (C) 2009-2013 Barchart, Inc. <http://www.barchart.com/>
 *
 * All rights reserved. Licensed under the OSI BSD License.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package com.tencent.bkrepo.udt.nio;

import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;

import com.tencent.bkrepo.udt.SocketUDT;
import com.tencent.bkrepo.udt.TypeUDT;

/**
 * Interface shared by all {@link KindUDT} kinds.
 */
public interface ChannelUDT extends Channel {

	/**
	 * Was connection request
	 * {@link SocketChannelUDT#connect(java.net.SocketAddress)} acknowledged by
	 * {@link SocketChannelUDT#finishConnect()}?
	 */
	boolean isConnectFinished();

	/**
	 * The kind of UDT channel.
	 */
	KindUDT kindUDT();

	/**
	 * UDT specific provider which produced this channel.
	 */
	SelectorProviderUDT providerUDT();

	/**
	 * Underlying UDT socket.
	 */
	SocketUDT socketUDT();

	/**
	 * The type of UDT socket.
	 */
	TypeUDT typeUDT();

	/**
	 * Mask of all interest options which are permitted for this channel.
	 * 
	 * @see SelectionKey
	 */
	int validOps();

}
