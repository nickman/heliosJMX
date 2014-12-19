jQuery(function($){

'use strict';

var KLAUS = window.KLAUS || {};

/* ==================================================
   Drop Menu
================================================== */

KLAUS.subMenu = function(){
	$('#menu ul').supersubs({
		minWidth: 12,
		maxWidth: 27,
		extraWidth: 0 // set to 1 if lines turn over
	}).superfish({
		delay: 0,
		animation: {opacity:'show'},
		speed: 'fast',
		autoArrows: false,
		dropShadows: false
	}).supposition();

	$('#menu ul .sub-menu li').each(function(){
		if($(this).find('ul.sub-menu').length > 0) {
			 $(this).find('> a').append('<i class="font-icon-arrow-right-simple-thin-round"></i>');
		}
	});
};

/* ==================================================
   Mobile Navigation
================================================== */
/* Clone Menu for use later */
var mobileMenuClone = $('#menu').clone().attr('id', 'navigation-mobile');

KLAUS.mobileNav = function(){
	var windowWidth = $(window).width();
	
	// Show Menu or Hide the Menu
	if( windowWidth >= 979 ) {
		$('#navigation-mobile').css('display', 'none');
		if ($('#mobile-nav').hasClass('open')) {
			$('#mobile-nav').removeClass('open');	
		}
	}
};

// Call the Event for Menu 
KLAUS.listenerMenu = function(){
	
	$('#mobile-nav').on('click', function(e){
		$(this).toggleClass('open');
		
		$('#navigation-mobile').stop().slideToggle(350, 'easeOutExpo');
		e.preventDefault();
	});
};

KLAUS.mobileMenu = function(){
	$('#menu-nav-mobile li').each(function(){
		if($(this).find('> ul').length > 0) {
			 $(this).addClass('has-ul').children('.sub-menu').hide();
			 $(this).find('> a').append('<i class="font-icon-arrow-down-simple-thin-round"></i>');
		}
	});

	$('#menu-nav-mobile li:has(">ul") > a').click(function(){
		$(this).parent().toggleClass('open');
		$(this).parent().find('> ul').stop(true,true).slideToggle();
		return false;
	});
};

/* ==================================================
   Filter Team
================================================== */

KLAUS.people = function (){
if($('#team-people').length > 0){      
    var $container = $('#team-people');

    $container.imagesLoaded(function() {
        $container.isotope({
          animationEngine : 'best-available',
          itemSelector : '.single-people',
          layoutMode: 'sloppyMasonry'
        });
    });


    // filter items when filter link is clicked
    var $optionSets = $('#team-filter .option-set'),
        $optionLinks = $optionSets.find('a');

      $optionLinks.click(function(){
        var $this = $(this);
        // don't proceed if already selected
        if ( $this.hasClass('selected') ) {
          return false;
        }
        var $optionSet = $this.parents('.option-set');
        $optionSet.find('.selected').removeClass('selected');
        $this.addClass('selected');

        // make option object dynamically, i.e. { filter: '.my-filter-class' }
        var options = {},
            key = $optionSet.attr('data-option-key'),
            value = $this.attr('data-option-value');
        // parse 'false' as false boolean
        value = value === 'false' ? false : value;
        options[ key ] = value;
        if ( key === 'layoutMode' && typeof changeLayoutMode === 'function' ) {
          // changes in layout modes need extra logic
          changeLayoutMode( $this, options );
        } else {
          // otherwise, apply new options
          $container.isotope( options );
        }

        return false;
    });
}
};

/* ==================================================
   Filter Portfolio
================================================== */

KLAUS.portfolio = function (){
if($('#portfolio-projects').length > 0){       
    var $container = $('#portfolio-projects');

    $container.imagesLoaded(function() {
        $container.isotope({
          // options
          animationEngine: 'best-available',
		  layoutMode: 'sloppyMasonry',
          itemSelector : '.item-project'
        });
    });
	
	$(window).smartresize(function() {
		$('#portfolio-projects').isotope('reLayout');
	});


    // filter items when filter link is clicked
    var $optionSets = $('#portfolio-filter .option-set'),
        $optionLinks = $optionSets.find('a');

      $optionLinks.click(function(){
        var $this = $(this);
        // don't proceed if already selected
        if ( $this.hasClass('selected') ) {
          return false;
        }
        var $optionSet = $this.parents('.option-set');
        $optionSet.find('.selected').removeClass('selected');
        $this.addClass('selected');

        // make option object dynamically, i.e. { filter: '.my-filter-class' }
        var options = {},
            key = $optionSet.attr('data-option-key'),
            value = $this.attr('data-option-value');
        // parse 'false' as false boolean
        value = value === 'false' ? false : value;
        options[ key ] = value;
        if ( key === 'layoutMode' && typeof changeLayoutMode === 'function' ) {
          // changes in layout modes need extra logic
          changeLayoutMode( $this, options );
        } else {
          // otherwise, apply new options
          $container.isotope( options );
        }

        return false;
    });
}
};


/* ==================================================
   Masonry Blog
================================================== */

KLAUS.masonryBlog = function (){
if($('.masonry-blog').length > 0){ 

	var $container = $('.masonry-area');

   
    $container.isotope({
      // options
      animationEngine: 'best-available',
	  layoutMode: 'sloppyMasonry',
      itemSelector : '.item-blog'
    });
    
	
	$(window).smartresize(function() {
		$container.isotope('reLayout');
	});

}
};


/* ==================================================
   DropDown 
================================================== */

KLAUS.dropDown = function(){
	$('.dropmenu').on('click', function(e){
		$(this).toggleClass('open');
		
		$('.dropmenu-active').stop().slideToggle(350, 'easeOutExpo');
		
		e.preventDefault();
	});
	
	// Dropdown
	$('.dropmenu-active a').on('click', function(e){
		var dropdown = $(this).parents('.dropdown');
		var selected = dropdown.find('.dropmenu .selected');
		var newSelect = $(this).html();
		
		$('.dropmenu').removeClass('open');
		$('.dropmenu-active').slideUp(350, 'easeOutExpo');
		
		selected.html(newSelect);
		
		e.preventDefault();
	});

	// Listed Portfolio
	$('.portfolio-right a').on('click', function(e){
		var portfolio_click = $('.portfolio-left');
		var portfolio_selected = portfolio_click.find('.selected');
		var portfolio_newSelect = $(this).html();
		
		portfolio_selected.html(portfolio_newSelect);
		
		e.preventDefault();
	});

	// Listed Team
	$('.team-right a').on('click', function(e){
		var team_click = $('.team-left');
		var team_selected = team_click.find('.selected');
		var team_newSelect = $(this).html();
		
		team_selected.html(team_newSelect);
		
		e.preventDefault();
	});
};

/* ==================================================
   Circular Graph 
================================================== */

KLAUS.circularGraph = function(){
	var chart = $(".chart");
	
	$(chart).each(function() {
		var currentChart = $(this),
			currentSize = currentChart.attr('data-size'),
			currentLine = currentChart.attr('data-line'),
			currentBgColor = currentChart.attr('data-bgcolor'),
			currentTrackColor = currentChart.attr('data-trackcolor');
		currentChart.easyPieChart({
			animate: 1000,
			barColor: currentBgColor,
			trackColor: currentTrackColor,
			lineWidth: currentLine,
			size: currentSize,
			lineCap: 'butt',
			scaleColor: false,
			onStep: function(value) {
          		this.$el.find('.percentage').text(~~value);
        	}
		});
	});

};


/* ==================================================
   FancyBox
================================================== */

KLAUS.fancyBox = function(){
	if($('.fancybox').length > 0 || $('.fancybox-media').length > 0 || $('.fancybox-various').length > 0){
		
		$(".fancybox").fancybox({				
			padding : 0,
			helpers : {
				title : { type: 'inside' },
			},
			afterLoad : function() {
                this.title = '<span class="counter-img">' + (this.index + 1) + ' / ' + this.group.length + '</span>' + (this.title ? '' + this.title : '');
            }
		});
			
		$('.fancybox-media').fancybox({
			padding : 0,
			helpers : {
				media : true
			},
			openEffect  : 'none',
			closeEffect : 'none',
			width       : 800,
    		height      : 450,
    		aspectRatio : true,
    		scrolling   : 'no'
		});
		
		$(".fancybox-various").fancybox({
			maxWidth	: 800,
			maxHeight	: 600,
			fitToView	: false,
			width		: '70%',
			height		: '70%',
			autoSize	: false,
			closeClick	: false,
			openEffect	: 'none',
			closeEffect	: 'none'
		});
	}
};

/* ==================================================
   Accordion
================================================== */

KLAUS.accordion = function(){
	if($('.accordion-builder').length > 0 ){
		var accordion_trigger = $('.accordion-heading.accordionize');
		
		accordion_trigger.delegate('.accordion-toggle','click', function(e){
			if($(this).hasClass('active')){
				$(this).removeClass('active');
				$(this).addClass('inactive');
			}
			else{
				accordion_trigger.find('.active').addClass('inactive');          
				accordion_trigger.find('.active').removeClass('active');   
				$(this).removeClass('inactive');
				$(this).addClass('active');
			}
			e.preventDefault();
		});
	}
};

/* ==================================================
   Toggle
================================================== */

KLAUS.toggle = function(){
	if($('.toggle-builder').length > 0 ){
		var accordion_trigger_toggle = $('.accordion-heading.togglize');
		
		accordion_trigger_toggle.delegate('.accordion-toggle','click', function(e){
			if($(this).hasClass('active')){
				$(this).removeClass('active');
				$(this).addClass('inactive');
			}
			else{
				$(this).removeClass('inactive');
				$(this).addClass('active');
			}
			e.preventDefault();
		});
	}
};

/* ==================================================
   Tabs
================================================== */

KLAUS.tabs = function(){
if($('.tabbable').length > 0 ){
    $('.tabbable').each(function() {
        $(this).find('li').first().addClass('active');
        $(this).find('.tab-pane').first().addClass('active'); 
    });
}
};

/* ==================================================
	Testimonial Sliders
================================================== */

KLAUS.testimonial = function(){
if($('.testimonial').length > 0 ){
	$('.testimonial').flexslider({
		animation:"fade",
		easing:"swing",
		controlNav: true, 
		reverse:false,
		smoothHeight:true,
		directionNav: false, 
		controlsContainer: '.az-testimonials-container',
		animationSpeed: 400
	});
}

if($('#twitter-feed .slides').length > 0 ){
	$('#twitter-feed').flexslider({
		animation:"fade",
		easing:"swing",
		controlNav: false, 
		reverse:false,
		smoothHeight:true,
		directionNav: false, 
		controlsContainer: '#twitter-feed',
		animationSpeed: 400
	});
}
};

/* ==================================================
	Big Twitter Feeds Slider
================================================== */

KLAUS.bigTweetSlide = function(){
if($('#twitter-feed .slides').length > 0 ){
	$('#twitter-feed').flexslider({
		animation:"fade",
		easing:"swing",
		controlNav: false, 
		reverse:false,
		smoothHeight:true,
		directionNav: false, 
		controlsContainer: '#twitter-feed',
		animationSpeed: 400
	});
}
};

/* ==================================================
   Tooltip
================================================== */

KLAUS.toolTip = function(){ 
    $('a[data-toggle=tooltip]').tooltip();
};

/* ==================================================
	Scroll to Top
================================================== */

KLAUS.scrollToTop = function(){

	if( $('#back-to-top').length > 0 ) {
		var didScroll = false;
		var $arrow = $('#back-to-top');

		$(window).scroll(function() {
			didScroll = true;
		});

		setInterval(function() {
			if( didScroll ) {
				didScroll = false;

				if( $(window).scrollTop() > 1000 ) {
					$arrow.appear(function() {
						$(this).addClass('opened');
					});
				} else {
					$arrow.removeClass('opened');
				}
			}
		}, 250);

		$arrow.click(function(e) {
			$('body,html').animate({ scrollTop: "0" }, 750, 'easeOutExpo' );
			e.preventDefault();
		});
	}
};

/* ==================================================
   Responsive Video
================================================== */

KLAUS.video = function(){
	$('.videoWrapper, .video-embed').fitVids();
};

/* ==================================================
	Custom Select
================================================== */

KLAUS.customSelect = function(){
	if($('.selectpicker').length > 0){
		$('.selectpicker').selectpicker();
	}
};

/* ==================================================
   MediaElements
================================================== */

KLAUS.mediaElements = function(){

$('audio, video').each(function(){
    $(this).mediaelementplayer({
    // if the <video width> is not specified, this is the default
    defaultVideoWidth: 480,
    // if the <video height> is not specified, this is the default
    defaultVideoHeight: 270,
    // if set, overrides <video width>
    videoWidth: -1,
    // if set, overrides <video height>
    videoHeight: -1,
    // width of audio player
    audioWidth: 400,
    // height of audio player
    audioHeight: 50,
    // initial volume when the player starts
    startVolume: 0.8,
    // path to Flash and Silverlight plugins
    pluginPath: theme_objects.base + '/_include/js/mediaelement/',
    // name of flash file
    flashName: 'flashmediaelement.swf',
    // name of silverlight file
    silverlightName: 'silverlightmediaelement.xap',
    // useful for <audio> player loops
    loop: false,
    // enables Flash and Silverlight to resize to content size
    enableAutosize: true,
    // the order of controls you want on the control bar (and other plugins below)
    // Hide controls when playing and mouse is not over the video
    alwaysShowControls: false,
    // force iPad's native controls
    iPadUseNativeControls: false,
    // force iPhone's native controls
    iPhoneUseNativeControls: false,
    // force Android's native controls
    AndroidUseNativeControls: false,
    // forces the hour marker (##:00:00)
    alwaysShowHours: false,
    // show framecount in timecode (##:00:00:00)
    showTimecodeFrameCount: false,
    // used when showTimecodeFrameCount is set to true
    framesPerSecond: 25,
    // turns keyboard support on and off for this instance
    enableKeyboard: true,
    // when this player starts, it will pause other players
    pauseOtherPlayers: true,
    // array of keyboard commands
    keyActions: []
    });
});

$('.video-wrap video').each(function(){
    $(this).mediaelementplayer({
    	enableKeyboard: false,
        iPadUseNativeControls: false,
        pauseOtherPlayers: true,
        iPhoneUseNativeControls: false,
        AndroidUseNativeControls: false
    });

    if (navigator.userAgent.match(/(Android|iPod|iPhone|iPad|IEMobile|Opera Mini)/)) {
	    $(".video-section-container .mobile-video-image").show();
	    $(".video-section-container .video-wrap").remove()
	}
});

$(".video-section-container .video-wrap").each(function (b) {
	var min_w = 1500;
	var header_height = 0;
	var vid_w_orig = 1280;
	var vid_h_orig = 720;
    
    var f = $(this).closest(".video-section-container").outerWidth();
    var e = $(this).closest(".video-section-container").outerHeight();
    $(this).width(f);
    $(this).height(e);
    var a = f / vid_w_orig;
    var d = (e - header_height) / vid_h_orig;
    var c = a > d ? a : d;
    min_w = 1280 / 720 * (e + 20);
    if (c * vid_w_orig < min_w) {
        c = min_w / vid_w_orig
    }
    $(this).find("video, .mejs-overlay, .mejs-poster").width(Math.ceil(c * vid_w_orig + 2));
    $(this).find("video, .mejs-overlay, .mejs-poster").height(Math.ceil(c * vid_h_orig + 2));
    $(this).scrollLeft(($(this).find("video").width() - f) / 2);
    $(this).find(".mejs-overlay, .mejs-poster").scrollTop(($(this).find("video").height() - (e)) / 2);
    $(this).scrollTop(($(this).find("video").height() - (e)) / 2)
});

};

KLAUS.resizeMediaElements = function(){
	var entryAudioBlog = $('.audio-thumb');
	var entryVideoBlog = $('.video-thumb');

	entryAudioBlog.each(function() { 
		$(this).css("width", $('article').width() + "px"); 
	}); 

	entryVideoBlog.each(function() { 
		$(this).css("width", $('article').width() + "px"); 
	}); 
};

/* ==================================================
	Menu Leave Page / Cache Back Button Reload
================================================== */

KLAUS.leavePage = function(){
	$('header #logo a, #menu li a').not('header #menu li a[href$="#"]').click(function(event){
		
		event.preventDefault();
		var linkLocation = this.href;

		$('header').animate({'opacity' : 0, 'marginTop': -150}, 500, 'easeOutExpo');
		$('#main').animate({'opacity' : 0}, 500, 'easeOutExpo');
		
		$('body').fadeOut(500, function(){
			window.location = linkLocation;
		});      
	}); 
};

KLAUS.reloader = function(){
	window.onpageshow = function(event) {
		if (event.persisted) {
			window.location.reload(); 
		}
	};	
};

/* ==================================================
	Animations Module
================================================== */

KLAUS.animationsModule = function(){
	
	function elementViewed(element) {
		if (Modernizr.touch && $(document.documentElement).hasClass('no-animation-effects')) {
			return true;
		}
		var elem = element,
			window_top = $(window).scrollTop(),
			offset = $(elem).offset(),
			top = offset.top;
		if ($(elem).length > 0) {
			if (top + $(elem).height() >= window_top && top <= window_top + $(window).height()) {
				return true;
			} else {
				return false;
			}
		}
	};
	
	function onScrollInterval(){
		var didScroll = false;
		$(window).scroll(function(){
			didScroll = true;
		});
		
		setInterval(function(){
			if (didScroll) {
				didScroll = false;
			}
			
			if($('.chart').length > 0 ){
				$('.chart').each(function() {
					var currentChart = $(this);
					if (elementViewed(currentChart)) {
						KLAUS.circularGraph(currentChart);
					}
				});	
			}
			
			if($('.animated-content').length > 0 ){
				$('.animated-content').each(function() {
					var currentObj = $(this);
					var delay = currentObj.data('delay');
					if (elementViewed(currentObj)) {
						currentObj.delay(delay).queue(function(){
							currentObj.addClass('animate');
						});
					}
				});
			}
			
		}, 250);
	};
	
	onScrollInterval();
};

/* ==================================================
   Social Share
================================================== */

KLAUS.reloadSocial = function(){

	if( $('.fb-like').length > 0 || $('.twitter-share-button').length > 0 || $('.g-plusone').length > 0 || $('.pinterest-share').length > 0) {

	    //Twitter
	    if (typeof (twttr) != 'undefined') {
	        twttr.widgets.load();
	    } else {
	        $.getScript('http://platform.twitter.com/widgets.js');
	    }

	    //Facebook
	    if (typeof (FB) != 'undefined') {
	        FB.init({ status: true, cookie: true, xfbml: true });
	    } else {
	        $.getScript("http://connect.facebook.net/en_US/all.js#xfbml=1", function () {
	            FB.init({ status: true, cookie: true, xfbml: true });
	        });
	    }

	    // Pinterest
	    if (typeof (pinterest) != 'undefined') {
		    pinterest.widgets.load();
		} else {
			$.getScript('http://assets.pinterest.com/js/pinit.js');
		}
	  
	    //Google - Note that the google button will not show if you are opening the page from disk - it needs to be http(s)
	    if (typeof (gapi) != 'undefined') {
	        $(".g-plusone").each(function () {
	            gapi.plusone.render($(this).get(0));
	        });
	    } else {
	        $.getScript('https://apis.google.com/js/plusone.js');
	    }

	}
};

/* ==================================================
   Animation Header on Scroll
================================================== */

KLAUS.animationHeader = function(){
	//if($('header.sticky-header').length > 0 ){
		$(window).scroll(function(){
	        var $this = $(this),
	            pos   = $this.scrollTop();
	        if (pos > 50){
	            $('header.sticky-header').addClass('nav-small');
	        } else {
	            $('header.sticky-header').removeClass('nav-small');
	        }
	    });
	//}
};

/* ==================================================
   Count Number 
================================================== */

KLAUS.count = function(){
	if($('.counter-number').length > 0 ){
		$('.counter-number').appear(function() {
			$('.timer').countTo();
		});
	}

	if($('.progress-bar').length > 0 ){
		$('.progress-bar').appear(function() {
			$('.bar').addClass('ole');
		});
	}
};

/* ==================================================
	Init
================================================== */

$(window).load(function(){
	if($('.animation-enabled').length > 0 ){
		KLAUS.leavePage();
	}
});

$(document).ready(function(){
	// Animation Transition Preload Page
	if($('.animation-enabled').length > 0 ){
		
		KLAUS.reloader();
		
		$('body').jpreLoader({
			splashID: "#jSplash",
			showSplash: true,
			showPercentage: true,
			autoClose: true,
			loaderVPos: "50%"
		}, function() {				
			$("header").delay(150).animate({'opacity' : 1, 'marginTop': 0}, 500, 'easeOutExpo', function(){
				$('#main').delay(150).animate({'opacity' : 1}, 500, 'easeOutExpo', function(){
					$('footer').animate({'opacity' : 1}, 500, 'easeOutExpo');
				});
			});
		});
	}

	// Set Portfolio Thumbnails Size
	if($('.item-project').length > 0 ){
		$(".item-project").imagesLoaded(function() {
	        $(".item-project").each(function () {
		        var e = $(".project-name", this).height(),
		            t = $(".project-name", this).width();
		        $(".project-name .va", this).css("height", e).css("width", t)
		    });
	    });
	}
	
	// Set Team Thumbnails Size
	if($('.single-people').length > 0 ){
		$(".single-people").imagesLoaded(function() {
	        $(".single-people").each(function () {
		        var e = $(".team-name", this).height(),
		            t = $(".team-name", this).width();
		        $(".team-name .va", this).css("height", e).css("width", t)
		    });
	    });
	}

	KLAUS.animationHeader();
	KLAUS.animationsModule();
	KLAUS.mediaElements();
	KLAUS.resizeMediaElements();
	KLAUS.video();
	KLAUS.dropDown();
	KLAUS.masonryBlog();
	KLAUS.people();
	KLAUS.portfolio();
	KLAUS.mobileNav();
	KLAUS.mobileMenu();
	KLAUS.listenerMenu();
	KLAUS.subMenu();
	KLAUS.accordion();
	KLAUS.toggle();
	KLAUS.tabs();
	KLAUS.testimonial();
	KLAUS.bigTweetSlide();
	KLAUS.toolTip();
	KLAUS.fancyBox();
	KLAUS.customSelect();
	KLAUS.count();
	KLAUS.scrollToTop();
	KLAUS.reloadSocial();
});

$(window).resize(function(){
	KLAUS.mobileNav();
	KLAUS.resizeMediaElements();

	// Resize Portfolio Thumbnails Size
	if($('.item-project').length > 0 ){
	    $(".item-project").each(function () {
	        var e = $(".project-name", this).height(),
	            t = $(".project-name", this).width();
	        $(".project-name .va", this).css("height", e).css("width", t)
	    });
	}
	// Resize Team Thumbnails Size
	if($('.single-people').length > 0 ){
	    $(".single-people").each(function () {
	        var e = $(".team-name", this).height(),
	            t = $(".team-name", this).width();
	        $(".team-name .va", this).css("height", e).css("width", t)
	    });
	}

	// Resize Video Background
	$(".video-section-container .video-wrap").each(function (b) {
		var min_w = 1500;
		var header_height = 0;
		var vid_w_orig = 1280;
		var vid_h_orig = 720;
	    
	    var f = $(this).closest(".video-section-container").outerWidth();
	    var e = $(this).closest(".video-section-container").outerHeight();
	    $(this).width(f);
	    $(this).height(e);
	    var a = f / vid_w_orig;
	    var d = (e - header_height) / vid_h_orig;
	    var c = a > d ? a : d;
	    min_w = 1280 / 720 * (e + 20);
	    if (c * vid_w_orig < min_w) {
	        c = min_w / vid_w_orig
	    }
	    $(this).find("video, .mejs-overlay, .mejs-poster").width(Math.ceil(c * vid_w_orig + 2));
	    $(this).find("video, .mejs-overlay, .mejs-poster").height(Math.ceil(c * vid_h_orig + 2));
	    $(this).scrollLeft(($(this).find("video").width() - f) / 2);
	    $(this).find(".mejs-overlay, .mejs-poster").scrollTop(($(this).find("video").height() - (e)) / 2);
	    $(this).scrollTop(($(this).find("video").height() - (e)) / 2)
	});
});

});