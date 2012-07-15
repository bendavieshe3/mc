/**
 * Media Clean
 * Iterates over a media collection, extracts a probable title and year, looks the media 
 * up on IMDB 
 */

 import java.util.regex.Pattern
 import groovy.json.*


println "Media Cleaner"
println "-------------"

def mediaFolderPath = args[0]

mediaFolderPath = args[0]
outputFolderPath = args.length == 2 ? args[1]:args[0]

File mediaFolder = new File(mediaFolderPath)
File outputFolder = new File(outputFolderPath)

if(!mediaFolder.exists() | !mediaFolder.isDirectory()) {
	showError("Invalid media folder path: ${mediaFolderPath}")
}

if(!outputFolder.exists() | !outputFolder.isDirectory()) {
	showError("Invalid output folder path: ${outputFolder}")
}

println "media folder: $mediaFolderPath"
println "output folder: $outputFolderPath"


MediaFile currentFile
MediaFileCollection mediaFiles = new MediaFileCollection()

int maxFilesToProcess = 500
int numFilesProcessed = 0
String[] allowedFileExtensions = ['avi','mp4','divX']
String[] skippedFiles = []

print 'Finding Files'
mediaFolder.eachFileRecurse {

	if(numFilesProcessed < maxFilesToProcess | maxFilesToProcess == 0) {

		def fileNamePair = it.name.tokenize('.')
		def fileExt = fileNamePair.size() > 1?fileNamePair[1]: ''

		if(!it.isDirectory() && it.name[0] != '.' && allowedFileExtensions.contains(fileExt.toLowerCase())) {
			currentFile =  new MediaFile(it)
			mediaFiles.add(currentFile)

			print '.'
			numFilesProcessed++
 		} else {
 			skippedFiles += it.name
 		}
	}	
}
println ''

println "Found ${mediaFiles.size()} media files"


def numImdbMatched = 0 
mediaFiles.process 'Looking up IMDB', { 
	if(it.lookupOnImdb()) { numImdbMatched++ }
}
println "Found ${numImdbMatched} matches on IMDB"


mediaFiles.list 'Matched Files:', {
	it.imdbMatched
}

mediaFiles.list 'Unmatched Files:', {
	!it.imdbMatched
}

println 'Skipped Files'
skippedFiles.each {
	println it
}
println ''

println 'Genres'
mediaFiles.genreList().each { genre ->

	mediaFiles.list "Genre $genre:",  { 
		it.genre.contains genre
	}
}

File outputList = new File("${outputFolder.canonicalPath}/mediaFiles.txt")
outputList.write('Media Files\n')
mediaFiles.process 'Writing output file', { mediaFile ->
	outputList.append(mediaFile.toLabelString() + '\n')
}



println "${mediaFiles.size()} files processed"


//------------------

def showError(msg) {
	println msg
	showUsage()
	System.exit(1)
}

def showUsage() {
	println "Usage: groovy mc.groovy mediaFolderPath"
}

class MediaFileCollection {

	def MediaFile[] mediaFiles = []

	def add(MediaFile mf) {
		mediaFiles += mf
	}

	def plus(MediaFile mf) {
		mediaFiles += mf
	}

	def each(Closure cl) {
		mediaFiles.each cl
	}

	def process(String title, Closure cl) {
		print title
		mediaFiles.each {
			cl(it)
			print '.'
		}
		println ''
		
	}

	def list(String title, Closure cl) {
		println title
		select(cl).each {
			println it.toLabelString()
		}
		println ''
	}

	def select(Closure cl) {
		mediaFiles.findAll cl
	}

	def size() {
		mediaFiles.size()
	}

	def genreList() {
		def genres = new HashSet()
		mediaFiles.each { mf ->
			mf.genre.each { genre ->
				genres.add genre
			}
		}
		genres
	}
}

class MediaFile {

	static String filenamePattern = /^([\w\s]+)(,\s([Tt]he|[aA])){0,1}\s*(\(([0-9]{4})\)){0,1}.*\.([a-zA-Z0-9]{3,4})$/
	/* This means:
	 * ^([\w\s]+) -- a title at the start made of whitespace and word characters
	 * \s* -- some whitespace	
	 * (\(([0-9]{4})\)){0,1} -- an optional year in brackets
	 * \W* -- some optional non-words
	 * \.([a-zA-Z0-9]{3,4})$ -- and an extension at the end
	 */

	static String extraWhitespacePattern = /\s{2,}/

	static String imdbApiUrlRoot = "http://www.imdbapi.com/"

	String title
	String sortTitle
	int year
	String filename = "unknown"
	String extension
	String[] genre = []
	String message = ''
	String runtime
	String article
	boolean imdbMatched

	def MediaFile(File file)
	{
		filename = file.name

		//match title, year, extension
		def nameMatcher = filename =~ filenamePattern
		if(nameMatcher) {
			title = nameMatcher[0][1]
			if(nameMatcher[0][3]) {
				article = nameMatcher[0][3]
			}
			if(nameMatcher[0][5]) {
				year = Integer.parseInt(nameMatcher[0][5])
			}
			extension = nameMatcher[0][6]
		}

		title = title?.replaceAll(Pattern.compile(extraWhitespacePattern), ' ')
		title = title?.replaceAll(Pattern.compile(/^\s+/), ' ')
		title = title?.replaceAll(Pattern.compile(/\s+$/), ' ')

		if(article) {
			sortTitle = "${title}, ${article}"
			title = "${article} ${title}"
		}


	}

	def lookupOnImdb() {
		if(title) {
			def queryUrl = "${imdbApiUrlRoot}?r=JSON&t=${URLEncoder.encode(title)}"
			if(year) { queryUrl += "&y=${URLEncoder.encode(year.toString())}" }
			
			try {
				def imdbJson = new URL(queryUrl).getText()
				def imdbData = new JsonSlurper().parseText(imdbJson)

				if(imdbData?.Title) {
					imdbMatched = true
					if(imdbData.Genre) { genre = imdbData.Genre.tokenize(', ') }
					if(imdbData.Year && !year) { year = Integer.parseInt(imdbData.Year)}
					if(imdbData.Runtime) { runtime = imdbData.Runtime}					
				} else {
					imdbMatched = false
				}


			} catch(Exception e) {
				message = e.getMessage()
			}

			
		}
		return !message
	}

	String toString() {
		"${title?:"unknown"} (${year?:"none"}, ${runtime}, ${genre}, ${extension}, ${filename}) ${message}"
	}

	String toLabelString() {
		def label = title
		if(year) {
			label += " (${year})"
		}
	}

}