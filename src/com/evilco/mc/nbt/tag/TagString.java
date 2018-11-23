package com.evilco.mc.nbt.tag;

import com.evilco.mc.nbt.stream.NbtInputStream;
import com.evilco.mc.nbt.stream.NbtOutputStream;


import java.io.IOException;

/**
 * @auhtor Johannes Donath <johannesd@evil-co.com>
 * @copyright Copyright (C) 2014 Evil-Co <http://www.evil-co.org>
 */
public class TagString extends AbstractTag {

	/**
	 * Stores the tag value.
	 */
	protected String value;

	/**
	 * Constructs a new TagString.
	 * @param name
	 * @param value
	 */
	public TagString ( String name,  String value) {
		super (name);
		this.setValue (value);
	}

	/**
	 * Constructs a new TagString.
	 * @param inputStream
	 * @param anonymous
	 * @throws IOException
	 */
	public TagString ( NbtInputStream inputStream, boolean anonymous) throws IOException {
		super (inputStream, anonymous);

		// read size
		int size = inputStream.readShort ();

		// read bytes
		byte[] data = new byte[size];
		inputStream.readFully (data);

		// store value
		this.setValue (new String (data, ITag.STRING_CHARSET));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte getTagID () {
		return TagType.STRING.typeID;
	}

	/**
	 * Returns the tag value.
	 * @return
	 */
	public String getValue () {
		return this.value;
	}

	/**
	 * Sets a new tag value.
	 * @param s
	 */
	public void setValue ( String s) {
		this.value = s;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write (NbtOutputStream outputStream, boolean anonymous) throws IOException {
		super.write (outputStream, anonymous);
	
		// write length - since getBytes can produce an array of a different length than the original string
		// the length of the output array is the one to write
		byte[] outputBytes = this.value.getBytes (ITag.STRING_CHARSET);
		outputStream.writeShort (outputBytes.length);

		// write string
		outputStream.write (outputBytes);
	}
}