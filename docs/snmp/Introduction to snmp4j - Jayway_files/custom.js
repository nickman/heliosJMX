(function($){
  $(document).ready(function(){

    // Fluid single image
    $('img.denk-fluid-image').each(function(){
      var $img = $(this);
      var $wrapper = $img.parents('.main-content');
      $wrapper.css('background-image', 'url('+$img.attr('src')+')').addClass('denk-fluid-image-wrapper');
    });


  });
})(jQuery);