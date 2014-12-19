(function ( $ ) {
  "use strict";

  $(function () {

    $('.grid-portfolio .hover-wrap').each(function(){
      var $wrap = $(this),
          $va = $wrap.find('.va:first'),
          $a = $wrap.find('a:first'),
          $a_content = $a.html()
      ;
      $wrap.wrapInner($a.html(''));
      $a.remove();
      $va.append('<div class="va-inner">'+$a_content+'</div>');
      $va.find('h3,h4').wrapInner('<span />');

    })

  });

}(jQuery));