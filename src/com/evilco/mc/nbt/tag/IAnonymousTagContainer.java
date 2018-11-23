package com.evilco.mc.nbt.tag;

import com.evilco.mc.nbt.error.UnexpectedTagTypeException;

import java.util.List;

/**
 * @auhtor Johannes Donath <johannesd@evil-co.com>
 * @copyright Copyright (C) 2014 Evil-Co <http://www.evil-co.org>
 */
public interface IAnonymousTagContainer extends ITagContainer {

	/**
	 * Adds a new tag.
	 * @param tag
	 */
	public void addTag ( ITag tag);

	/**
	 * Returns a list of all tags.
	 * @return
	 */
	public List<ITag> getTags ();
	
	/**
	 * Gets all tags in this container, ensuring their type is as expected
	 * @param tagClass the expected tag type of the contents
	 * @return the tags in this container
	 * @throws com.evilco.mc.nbt.error.UnexpectedTagTypeException at least one tag in this container is not of the expected type
	 */
	public <T extends ITag> List<T> getTags(Class<T> tagClass) throws UnexpectedTagTypeException;

	/**
	 * Sets a tag.
	 * @param i
	 * @param tag
	 */
	public void setTag (int i,  ITag tag);
}