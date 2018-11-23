package com.evilco.mc.nbt.tag;

import com.evilco.mc.nbt.stream.NbtOutputStream;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * @auhtor Johannes Donath <johannesd@evil-co.com>
 * @copyright Copyright (C) 2014 Evil-Co <http://www.evil-co.org>
 */
public interface ITag {

	/**
	 * Defines the string charset.
	 */
	public static final Charset STRING_CHARSET = Charset.forName ("UTF-8");

	/**
	 * Returns the tag name.
	 * @return The tag identifier.
	 */
	public String getName ();

	/**
	 * Returns the name bytes.
	 * @return Name string in UTF-8 bytes.
	 */
	public byte[] getNameBytes ();

	/**
	 * Returns the parent tag.
	 * @return The parent tag container (if any).
	 */
	public ITagContainer getParent ();

	/**
	 * Returns the tag ID.
	 * @return The tag numerical identifier.
	 */
	public byte getTagID ();

	/**
	 * Sets a new name.
	 * @param name The new name.
	 */
	public void setName ( String name);

	/**
	 * Sets a new parent.
	 * @param parent The new parent tag container.
	 */
	public void setParent ( ITagContainer parent);

	/**
	 * Writes a tag into a byte buffer.
	 * @param outputStream
	 * @param anonymous
	 */
	public void write (NbtOutputStream outputStream, boolean anonymous) throws IOException;
}