/*
* Manual background correction tool
* This tool helps correcting inhomogenious brightness
* distributions within a single SEM-image by selecting
* the same phases at different image sections
*
* © 2019 Florian Kleiner
*   Bauhaus-Universität Weimar
*   Finger-Institut für Baustoffkunde
*
* programmed using Fiji/ImageJ 1.52k
*
*/
// Prototype plugin tool. There are more plugin tools at
// http://imagej.nih.gov/ij/plugins/index.html#tools
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.tool.PlugInTool;
import java.awt.event.*;
import java.util.Arrays;

public class ManualBackgroundCorrection extends PlugInTool {

	// TODO make rows & columns user changable!
	protected int rows = 3;
	protected int columns = 3;
	
	protected double[][] colorSpecimenArray = new double[columns][rows];
	protected double magnification;
	protected int minColor = 255;
	protected int width = 0;
	protected int height = 0;
	protected String lastImageTitle = "";

	/**Converts a screen x-coordinate to an offscreen x-coordinate.*/
	private int convertMousePos(int sx) {
		return (int)(sx/magnification);
	}

	private void resetColorSpecimenArray() {
		// initiating or resetting colorSpecimenArray
		for ( int i = 0; i < columns; i++ ) {
			for ( int j = 0; j < rows; j++ ) {
				colorSpecimenArray[i][j] = (double)-1;
			}
		}
	}

	private void getDarkestColor() {
		minColor = 255;
		for ( int i = 0; i < columns; i++ ) {
			for ( int j = 0; j < rows; j++ ) {
				if ( minColor > colorSpecimenArray[i][j] ) {
					minColor = (int)colorSpecimenArray[i][j];
				}
			}
		}
	}

	public void mousePressed(ImagePlus imp, MouseEvent e) {
		//IJ.log("mouse pressed: "+e);
		ImageWindow iw = imp.getWindow();
		magnification = iw.getInitialMagnification();
		String imageTitle = imp.getTitle();
		width = imp.getWidth();
		height = imp.getHeight();
		
		// get mouse position in the image as pixel coordinates (depending on magnification)
		int x = convertMousePos( e.getX() );
		int y = convertMousePos( e.getY() );
		
		// get sector positions
		int sectorPosX = 0;
		int sectorPosY = 0;
		for ( int i = 1; i <= columns; i++ ) {
			if ( ( width / columns )*i > x ) {
				sectorPosX = i-1;
				break;
			}
		}
		for ( int j = 1; j <= rows; j++ ) {
			if ( ( height / rows )*j > y ) {
				sectorPosY = j-1;
				break;
			}
		}
		
		// initiating or resetting colorSpecimenArray
		if ( imageTitle != lastImageTitle ) {
			if ( lastImageTitle != "" ) {
				IJ.log( "Analysed Image has changed. Reset color selection..." );
			} else {
				IJ.log( "Started manual background correction tool." );
				IJ.log( " using " + columns + " x " + rows + " sectors" );
				IJ.log( " select in all sectors a position which is supposed to be the same phase and grey value" );
			}
			lastImageTitle = imageTitle;
			resetColorSpecimenArray();
		}
		
		// calculating a mean value by using a 5 by 5 neighbourhood of the selected pixel
		int valueSum = 0;
		int valCount = 0;
		int[] value;
		for ( int i = -2; i < 2; i++ ) {
			for ( int j = -2; j < 2; j++ ) {
				value = imp.getPixel( x+i, y+j ); // 4 element array - value[0] returns grayscale value
				valueSum += value[0];
				valCount++;
			}
		}
		double meanValue = valueSum/valCount;
		
		// set the mean color value o the colorSpecimenArray at the selected sector position
		colorSpecimenArray[sectorPosX][sectorPosY] = meanValue;		
		IJ.log( " - selected color " + meanValue + " at image position " + x + " x " + y + " in sektor " + (sectorPosX+1) + " x " + (sectorPosY+1) + "");

		// check if still some measurements are missing
		boolean calcBackground = true;
		outerloop:
		for ( int i = 0; i < columns; i++ ) {
			for ( int j = 0; j < rows; j++ ) {
				if ( colorSpecimenArray[i][j] == (double)-1 ) {
					IJ.log( "    still missing some values! eg. sektor " + (i+1) + " x " + (j+1) );
					calcBackground = false;
					break outerloop;
				}
			}
		}

		// calculate image correction if all measurements are present
		if ( calcBackground ) {
			IJ.log( " - calculating correction background!!" );
			IJ.wait(10);
			ImagePlus backgroundIMP = createBackgroundImage(imageTitle);
			IJ.log( " - calculating corrected Image!!" );
			createCorrectedImage(imageTitle, imp, backgroundIMP );
			IJ.log( " - Plugin is done. You still can change selections to optimize the result." );
		}
		
	}

	public ImagePlus createBackgroundImage(String imageTitle) {
		int stacks = 1;
		String title = "Background of " + imageTitle;
		ImagePlus backgroundIMP = NewImage.createByteImage (title , width, height, 1, NewImage.FILL_WHITE);
		
		ImageProcessor backgroundIP = backgroundIMP.getProcessor();

		getDarkestColor();
		
		int color, x, y, i, j;
		//get width and heigt of a single sector
		int sectorWidth  = (int)width/(columns-1)+1;
		int sectorHeight = (int)height/(rows-1)+1;
		//IJ.log( "middle?" + positionX + " | " + width );
		double factorX, factorY, colorLineA, colorLineB;
		i=0;
		j=0;
		for (y=0; y<height; y++) {
			for (x=0; x<width; x++) {
				// calculate sector position
				i = (int)Math.floor( x / sectorWidth );
				j = (int)Math.floor( y / sectorHeight );
				// calculate intensity factor
				factorX = (double)( x - sectorWidth  * i ) / sectorWidth;
				factorY = (double)( y - sectorHeight * j ) / sectorHeight;
				//mixing sector colors depending on position
				colorLineA = (double) colorSpecimenArray[i][j]   * ( 1 - factorX ) + colorSpecimenArray[i+1][j]   * factorX;
				colorLineB = (double) colorSpecimenArray[i][j+1] * ( 1 - factorX ) + colorSpecimenArray[i+1][j+1] * factorX;
				color = (int)Math.floor( colorLineA * ( 1 - factorY ) + colorLineB * factorY ) - minColor;
				// set pixel color
				backgroundIP.putPixel(x,y,color); 
			}
		}
		IJ.log( "    done creating BackgroundImage: " );
		backgroundIMP.show();
		IJ.selectWindow( title );
		return backgroundIMP;
	}
	
	public boolean createCorrectedImage(String imageTitle, ImagePlus sourceIMP, ImagePlus backgroundIMP ) {
		int stacks = 1;
		String title = imageTitle + " (corrected)";
		ImagePlus myIMP = NewImage.createByteImage (title , width, height, 1, NewImage.FILL_WHITE);
		ImageProcessor myNP = myIMP.getProcessor();
		
		int color, x, y;
		int[] colorSource;
		int[] colorBackground;
		for (y=0; y<height; y++) {
			for (x=0; x<width; x++) {
				colorSource = sourceIMP.getPixel( x, y );
				colorBackground =backgroundIMP.getPixel( x, y );
				color = colorSource[0] - colorBackground[0];
				if ( color < 0 ) color = 0;
				myNP.putPixel(x,y,color);
			}
		}
		IJ.log( "    done creating corrected image " );
		myIMP.show();
		IJ.selectWindow( title );
		return true;
	}
	public void showOptionsDialog() {
		IJ.log(" - reset colorSpecimenArray!");
		resetColorSpecimenArray();
	}
}
