/*
 *  Dictan Open Dictionary Java Library presents the core interface and functionality for dictionaries. 
 *	
 *  Copyright (C) 2010 - 2015  Dmitry Viktorov <dmitry.viktorov@softex.info> <http://www.softex.info>
 *	
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License (LGPL) as 
 *  published by the Free Software Foundation, either version 3 of the License, 
 *  or any later version.
 *	
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU Lesser General Public License for more details.
 *	
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package info.softex.dictionary.core.formats.dsl.testutils;

import info.softex.dictionary.core.formats.dsl.DSLBaseWriter;

import java.io.File;
import java.io.IOException;

/**
 * 
 * @since version 4.6, 02/27/2015
 * 
 * @author Dmitry Viktorov
 * 
 */
public class DSLBaseWriterWrapper extends DSLBaseWriter {

	public DSLBaseWriterWrapper(File inOutDirectory) throws IOException {
		super(inOutDirectory);
	}

}
