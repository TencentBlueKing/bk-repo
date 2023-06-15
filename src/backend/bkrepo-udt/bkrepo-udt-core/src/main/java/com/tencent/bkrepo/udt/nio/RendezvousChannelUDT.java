/**
 * Copyright (C) 2009-2013 Barchart, Inc. <http://www.barchart.com/>
 *
 * All rights reserved. Licensed under the OSI BSD License.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package com.tencent.bkrepo.udt.nio;

import java.nio.channels.SocketChannel;

import com.tencent.bkrepo.udt.ExceptionUDT;
import com.tencent.bkrepo.udt.SocketUDT;
import com.tencent.bkrepo.udt.TypeUDT;

/**
 * {@link SocketChannel}-like wrapper for {@link SocketUDT}, can be either
 * stream or message oriented, depending on {@link TypeUDT}
 * <p>
 * See <a href="http://udt.sourceforge.net/udt4/doc/t-firewall.htm">Firewall
 * Traversing with UDT</a>
 */
public class RendezvousChannelUDT extends SocketChannelUDT implements
		ChannelUDT {

	/**
	 * Ensure rendezvous mode.
	 */
	protected RendezvousChannelUDT( //
			final SelectorProviderUDT provider, //
			final SocketUDT socketUDT //
	) throws ExceptionUDT {

		super(provider, socketUDT);

		socketUDT.setReuseAddress(true);
		socketUDT.setRendezvous(true);

	}

	@Override
	public KindUDT kindUDT() {
		return KindUDT.RENDEZVOUS;
	}

}
