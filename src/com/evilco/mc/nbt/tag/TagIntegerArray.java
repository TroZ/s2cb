package com.evilco.mc.nbt.tag;

import com.evilco.mc.nbt.stream.NbtInputStream;
import com.evilco.mc.nbt.stream.NbtOutputStream;
import com.google.common.base.Preconditions;

import java.io.IOException;

/**
 * @auhtor Johannes Donath <johannesd@evil-co.com>
 * @copyright Copyright (C) 2014 Evil-Co <http://www.evil-co.org>
 */
public class TagIntegerArray extends AbstractTag {

	/**
	 * Stores the tag values.
	 */
	protected int[] values;

	/**
	 * Constructs a new TagIntegerArray.
	 * @param name
	 * @param values
	 */
	public TagIntegerArray ( String name, int[] values) {
		super (name);
		this.setValues (values);
	}

	/**
	 * Constructs a new TagIntegerArray.
	 * @param inputStream
	 * @param anonymous
	 * @throws IOException
	 */
	public TagIntegerArray ( NbtInputStream inputStream, boolean anonymous) throws IOException {
		super (inputStream, anonymous);

		// get size
		int size = inputStream.readInt ();

		// create array
		int[] data = new int[size];

		// read data
		for (int i = 0; i < size; i++) {
			data[i] = inputStream.readInt ();
		}
		
		// 
		this.values = data;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte getTagID () {
		return TagType.INTEGER_ARRAY.typeID;
	}

	/**
	 * Returns the tag values.
	 * @return
	 */
	public int[] getValues () {
		return this.values;
	}

	/**
	 * Sets new tag values.
	 * @param i
	 */
	public void setValues ( int[] i) {
		// check arguments
		Preconditions.checkNotNull (i, "i");

		// save value
		this.values = i;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write (NbtOutputStream outputStream, boolean anonymous) throws IOException {
		super.write (outputStream, anonymous);

		// write size
		outputStream.writeInt (this.values.length);

		// write data
		for (int i : this.values) {
			outputStream.writeInt (i);
		}
	}
}