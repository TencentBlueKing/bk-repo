/**
 * Copyright (C) 2009-2013 Barchart, Inc. <http://www.barchart.com/>
 *
 * All rights reserved. Licensed under the OSI BSD License.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package bench.carsh;

import com.tencent.bkrepo.udt.ExceptionUDT;
import com.tencent.bkrepo.udt.SocketUDT;
import com.tencent.bkrepo.udt.TypeUDT;

public class MainCrashJVM extends SocketUDT {

	public MainCrashJVM(final TypeUDT type) throws ExceptionUDT {
		super(type);
	}

	public static void main(final String[] args) {

		log.info("started; trying to crash jvm");

		try {

			// this will kill the jvm
			SocketUDT.testCrashJVM0();

		} catch (final Throwable e) {
			log.error("unexpected", e);
		}

	}

}
