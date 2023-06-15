/**
 * Copyright (C) 2009-2013 Barchart, Inc. <http://www.barchart.com/>
 *
 * All rights reserved. Licensed under the OSI BSD License.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package com.tencent.bkrepo.udt.net;

import com.tencent.bkrepo.udt.SocketUDT;

interface ServiceFactory {

	StreamService newService(SocketUDT socket) throws Exception;

}
