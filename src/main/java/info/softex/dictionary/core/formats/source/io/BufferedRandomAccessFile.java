/*
 *  Dictan Open Dictionary Java Library presents the core interface and functionality for dictionaries. 
 *	
 *  Copyright (C) 2010 - 2012  Dmitry Viktorov <dmitry.viktorov@softex.info> <http://www.softex.info>
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

package info.softex.dictionary.core.formats.source.io;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * A <code>BufferedRandomAccessFile</code> is like a
 * <code>RandomAccessFile</code>, but it uses a private buffer so that most
 * operations do not require a disk access.
 * <P>
 * 
 * Note: The operations on this class are unmonitored. Also, the correct
 * functioning of the <code>RandomAccessFile</code> methods that are not
 * overridden here relies on the implementation of those methods in the
 * superclass.
 */
public class BufferedRandomAccessFile extends RandomAccessFile {
	static final int LogBuffSz_ = 16; // 64K buffer
	public static final int BuffSz_ = (1 << LogBuffSz_);

	private String path_;

	/*
	 * This implementation is based on the buffer implementation in Modula-3's
	 * "Rd", "Wr", "RdClass", and "WrClass" interfaces.
	 */
	private boolean dirty_; // true iff unflushed bytes exist
	private boolean syncNeeded_; // dirty_ can be cleared by e.g. seek, so track
									// sync separately
	private long curr_; // current position in file
	private long lo_, hi_; // bounds on characters in "buff"
	private byte[] buff_; // local buffer
	private long maxHi_; // this.lo + this.buff.length
	private boolean hitEOF_; // buffer contains last file block?
	private long diskPos_; // disk position
	private long fileLength = -1; // cache for file size

	/*
	 * To describe the above fields, we introduce the following abstractions for
	 * the file "f":
	 * 
	 * len(f) the length of the file curr(f) the current position in the file
	 * c(f) the abstract contents of the file disk(f) the contents of f's
	 * backing disk file closed(f) true iff the file is closed
	 * 
	 * "curr(f)" is an index in the closed interval [0, len(f)]. "c(f)" is a
	 * character sequence of length "len(f)". "c(f)" and "disk(f)" may differ if
	 * "c(f)" contains unflushed writes not reflected in "disk(f)". The flush
	 * operation has the effect of making "disk(f)" identical to "c(f)".
	 * 
	 * A file is said to be *valid* if the following conditions hold:
	 * 
	 * V1. The "closed" and "curr" fields are correct:
	 * 
	 * f.closed == closed(f) f.curr == curr(f)
	 * 
	 * V2. The current position is either contained in the buffer, or just past
	 * the buffer:
	 * 
	 * f.lo <= f.curr <= f.hi
	 * 
	 * V3. Any (possibly) unflushed characters are stored in "f.buff":
	 * 
	 * (forall i in [f.lo, f.hi): c(f)[i] == f.buff[i - f.lo])
	 * 
	 * V4. For all characters not covered by V3, c(f) and disk(f) agree:
	 * 
	 * (forall i in [f.lo, len(f)): i not in [f.lo, f.hi) => c(f)[i] ==
	 * disk(f)[i])
	 * 
	 * V5. "f.dirty" is true iff the buffer contains bytes that should be
	 * flushed to the file; by V3 and V4, only part of the buffer can be dirty.
	 * 
	 * f.dirty == (exists i in [f.lo, f.hi): c(f)[i] != f.buff[i - f.lo])
	 * 
	 * V6. this.maxHi == this.lo + this.buff.length
	 * 
	 * Note that "f.buff" can be "null" in a valid file, since the range of
	 * characters in V3 is empty when "f.lo == f.hi".
	 * 
	 * A file is said to be *ready* if the buffer contains the current position,
	 * i.e., when:
	 * 
	 * R1. !f.closed && f.buff != null && f.lo <= f.curr && f.curr < f.hi
	 * 
	 * When a file is ready, reading or writing a single byte can be performed
	 * by reading or writing the in-memory buffer without performing a disk
	 * operation.
	 */

	/**
	 * Open a new <code>BufferedRandomAccessFile</code> on <code>file</code> in
	 * mode <code>mode</code>, which should be "r" for reading only, or "rw" for
	 * reading and writing.
	 */
	public BufferedRandomAccessFile(File file, String mode) throws IOException {
		this(file, mode, 0);
	}

	public BufferedRandomAccessFile(File file, String mode, int size)
			throws IOException {
		super(file, mode);
		path_ = file.getAbsolutePath();
		this.init(size, mode);
	}

	/**
	 * Open a new <code>BufferedRandomAccessFile</code> on the file named
	 * <code>name</code> in mode <code>mode</code>, which should be "r" for
	 * reading only, or "rw" for reading and writing.
	 */
	public BufferedRandomAccessFile(String name, String mode) throws IOException {
		this(name, mode, 0);
	}

	public BufferedRandomAccessFile(String name, String mode, int size) throws IOException {
		super(name, mode);
		path_ = name;
		this.init(size, mode);
	}

	private void init(int size, String mode) throws IOException {
		this.dirty_ = false;
		this.lo_ = this.curr_ = this.hi_ = 0;
		this.buff_ = (size > BuffSz_) ? new byte[size] : new byte[BuffSz_];
		this.maxHi_ = (long) BuffSz_;
		this.hitEOF_ = false;
		this.diskPos_ = 0L;
		if ("r".equals(mode)) {
			// read only file, we can cache file length
			this.fileLength = super.length();
		}
	}

	public String getPath() {
		return path_;
	}

	public void sync() throws IOException {
		if (syncNeeded_) {
			flush();
			getChannel().force(true); // true, because file length counts as
										// "metadata"
			syncNeeded_ = false;
		}
	}

	public void close() throws IOException {
		sync();
		this.buff_ = null;
		super.close();
	}

	/* Flush any dirty bytes in the buffer to disk. */
	public void flush() throws IOException {
		if (this.dirty_) {
			if (this.diskPos_ != this.lo_)
				super.seek(this.lo_);
			int len = (int) (this.hi_ - this.lo_);
			super.write(this.buff_, 0, len);
			this.diskPos_ = this.hi_;
			this.dirty_ = false;
		}
	}

	/*
	 * Read at most "this.buff.length" bytes into "this.buff", returning the
	 * number of bytes read. If the return result is less than
	 * "this.buff.length", then EOF was read.
	 */
	private int fillBuffer() throws IOException {
		int count = 0;
		int remainder = this.buff_.length;
		while (remainder > 0) {
			int n = super.read(this.buff_, count, remainder);
			if (n < 0)
				break;
			count += n;
			remainder -= n;
		}
		this.hitEOF_ = (count < this.buff_.length);
		this.diskPos_ += count;
		return count;
	}

	public void seek(long pos) throws IOException {
		this.curr_ = pos;
	}

	/*
	 * On exit from this routine <code>this.curr == this.hi</code> iff
	 * <code>pos</code> is at or past the end-of-file, which can only happen if
	 * the file was opened in read-only mode.
	 */
	private void reBuffer() throws IOException {
		this.flush();
		this.lo_ = this.curr_;
		this.maxHi_ = this.lo_ + (long) this.buff_.length;
		if (this.diskPos_ != this.lo_) {
			super.seek(this.lo_);
			this.diskPos_ = this.lo_;
		}
		int n = this.fillBuffer();
		this.hi_ = this.lo_ + (long) n;
	}

	public long getFilePointer() {
		return this.curr_;
	}

	public long length() throws IOException {
		if (fileLength == -1) {
			// max accounts for the case where we have written past the old file
			// length, but not yet flushed our buffer
			return Math.max(this.curr_, super.length());
		} else {
			// opened as read only, file length is cached
			return fileLength;
		}
	}

	public int read() throws IOException {
		if (this.lo_ > this.curr_ || this.curr_ >= this.hi_) {
			this.reBuffer();
			if (this.curr_ == this.hi_ && this.hitEOF_)
				return -1;
		}
		byte res = this.buff_[(int) (this.curr_ - this.lo_)];
		this.curr_++;
		return ((int) res) & 0xFF; // convert byte -> int
	}

	public int read(byte[] b) throws IOException {
		return this.read(b, 0, b.length);
	}

	public int read(byte[] b, int off, int len) throws IOException {
		if (this.lo_ > this.curr_ || this.curr_ >= this.hi_) {
			this.reBuffer();
			if (this.curr_ == this.hi_ && this.hitEOF_)
				return -1;
		}
		len = Math.min(len, (int) (this.hi_ - this.curr_));
		int buffOff = (int) (this.curr_ - this.lo_);
		System.arraycopy(this.buff_, buffOff, b, off, len);
		this.curr_ += len;
		return len;
	}

	public void write(int b) throws IOException {
		if (this.lo_ > this.curr_ || this.curr_ > this.hi_
				|| this.curr_ >= maxHi_) {
			this.reBuffer();
		}
		this.buff_[(int) (this.curr_ - this.lo_)] = (byte) b;
		this.curr_++;
		if (this.curr_ > this.hi_)
			this.hi_ = this.curr_;
		this.dirty_ = true;
		syncNeeded_ = true;
	}

	public void write(byte[] b) throws IOException {
		this.write(b, 0, b.length);
	}

	public void write(byte[] b, int off, int len) throws IOException {
		while (len > 0) {
			int n = this.writeAtMost(b, off, len);
			off += n;
			len -= n;
			this.dirty_ = true;
			syncNeeded_ = true;
		}
	}

	/*
	 * Write at most "len" bytes to "b" starting at position "off", and return
	 * the number of bytes written. caller is responsible for setting dirty,
	 * syncNeeded.
	 */
	private int writeAtMost(byte[] b, int off, int len) throws IOException {
		if (this.lo_ > this.curr_ || this.curr_ > this.hi_ || this.curr_ >= maxHi_) {
			this.reBuffer();
		}
		len = Math.min(len, (int) (this.maxHi_ - this.curr_));
		int buffOff = (int) (this.curr_ - this.lo_);
		System.arraycopy(b, off, this.buff_, buffOff, len);
		this.curr_ += len;
		if (this.curr_ > this.hi_) {
			this.hi_ = this.curr_;
		}
		return len;
	}

	public boolean isEOF() throws IOException {
		return getFilePointer() == length();
	}

	public long bytesRemaining() throws IOException {
		return length() - getFilePointer();
	}
	
}