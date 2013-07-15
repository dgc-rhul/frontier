/*******************************************************************************
 * Copyright (c) 2013 Raul Castro Fernandez (Ra).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *    Ra - Design and initial implementation
 ******************************************************************************/
package uk.co.imperial.lsds.seep.comm.serialization;

import com.esotericsoftware.kryo.Kryo;


public class KryoSerializer<T> implements SeePSerializer<T> {
	
	public Kryo k = new Kryo();
	
	@Override
	public T deserialize(byte[] data) throws SeePSerializationException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public byte[] serialize(T data) throws SeePSerializationException {
		// TODO Auto-generated method stub
		return null;
	}

}
