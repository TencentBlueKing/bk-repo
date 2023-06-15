/**
 * Copyright (C) 2009-2013 Barchart, Inc. <http://www.barchart.com/>
 *
 * All rights reserved. Licensed under the OSI BSD License.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package com.tencent.bkrepo.udt.net;

import com.tencent.bkrepo.udt.ErrorUDT;
import com.tencent.bkrepo.udt.ExceptionUDT;

/**
 * 
 */
@SuppressWarnings("serial")
public class ExceptionReceiveUDT extends ExceptionUDT {

	protected ExceptionReceiveUDT(final int socketID, final ErrorUDT error,
			final String comment) {
		super(socketID, error, comment);
	}

}
