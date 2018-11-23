package com.evilco.mc.nbt.tag;

import java.io.IOException;

import com.evilco.mc.nbt.stream.NbtInputStream;
import com.evilco.mc.nbt.stream.NbtOutputStream;
import com.google.common.base.Preconditions;


/**
 * @auhtor Johannes Donath <johannesd@evil-co.com>
 * @copyright Copyright (C) 2014 Evil-Co <http://www.evil-co.org>
 */
public abstract class AbstractTag implements ITag {

	/**
	 * Stores the tag name.
	 */
	protected String name;

	/**
	 * Stores the parent tag.
	 */
	protected ITagContainer parent = null;

	/**
	 * Constructs a new AbstractTag.
	 * @param name The tag name.
	 */
	public AbstractTag ( String name) {
		this.setName (name);
	}

	/**
	 * Constructs a new AbstractTag.
	 * @param inputStream
	 * @param anonymous
	 * @throws java.io.IOException
	 */
	public AbstractTag ( NbtInputStream inputStream, boolean anonymous) throws IOException {
		// validate arguments
		Preconditions.checkNotNull (inputStream, "inputStream");

		// read name
		if (!anonymous) {
			// read name size
			int nameSize = inputStream.readShort ();
			byte[] nameBytes = new byte[nameSize];

			// read name
			inputStream.readFully (nameBytes);
			this.setName (new String (nameBytes, STRING_CHARSET));
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getName () {
		return this.name;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte[] getNameBytes () {
		return this.name.getBytes (STRING_CHARSET);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ITagContainer getParent () {
		return this.parent;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public abstract byte getTagID ();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setName ( String name) {
		// check arguments
		Preconditions.checkNotNull (name, "name");

		// remove previous tag
		if (this.getParent () != null) this.getParent ().removeTag (this);

		// update name
		this.name = name;

		// add new tag
		if (this.getParent () != null) {
			if (this.getParent () instanceof IAnonymousTagContainer)
				((IAnonymousTagContainer) this.getParent ()).addTag (this);
			else
				((INamedTagContainer) this.getParent ()).setTag (this);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setParent ( ITagContainer parent) {
		// remove from old parent
		if (this.getParent () != null) this.getParent ().removeTag (this);

		// set new parent
		this.parent = parent;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write (NbtOutputStream outputStream, boolean anonymous) throws IOException {
		// write name
		if (!anonymous) {
			// get name
			byte[] name = this.getNameBytes ();

			// write size
			outputStream.writeShort (name.length);

			// write bytes
			outputStream.write (name);
		}
	};
}