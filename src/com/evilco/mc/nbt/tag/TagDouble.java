package com.evilco.mc.nbt.tag;

import com.evilco.mc.nbt.stream.NbtInputStream;
import com.evilco.mc.nbt.stream.NbtOutputStream;

import java.io.IOException;

/**
 * @auhtor Johannes Donath <johannesd@evil-co.com>
 * @copyright Copyright (C) 2014 Evil-Co <http://www.evil-co.org>
 */
public class TagDouble extends AbstractTag {

	/**
	 * Stores the tag value.
	 */
	protected double value;

	/**
	 * Constructs a new TagDouble.
	 * @param name
	 * @param value
	 */
	public TagDouble ( String name, double value) {
		super (name);
		this.setValue (value);
	}

	/**
	 * Constructs a new TagDouble.
	 * @param inputStream
	 * @param anonymous
	 * @throws IOException
	 */
	public TagDouble ( NbtInputStream inputStream, boolean anonymous) throws IOException {
		super (inputStream, anonymous);

		this.setValue (inputStream.readDouble ());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte getTagID () {
		return TagType.DOUBLE.typeID;
	}

	/**
	 * Returns the tag value.
	 * @return
	 */
	public double getValue () {
		return this.value;
	}

	/**
	 * Sets a new tag value.
	 * @param d
	 */
	public void setValue (double d) {
		this.value = d;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write (NbtOutputStream outputStream, boolean anonymous) throws IOException {
		super.write (outputStream, anonymous);

		// write double
		outputStream.writeDouble (this.value);
	}
}