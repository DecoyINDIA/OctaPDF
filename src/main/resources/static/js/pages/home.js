updateFavoriteIcons();
const contentPath = /*[[${@contextPath}]]*/ '';

document.addEventListener('DOMContentLoaded', function () {
  if (window.location.pathname === '/') {
    const navItem = document.getElementById('navItemToHide');
    if (navItem) {
      navItem.style.display = 'none';
    }
  }
  updateFavoritesDropdown();
  toolsManager();
});

function setAsDefault(value) {
  localStorage.setItem('defaultView', value);
  console.log(`Default view set to: ${value}`);
}

function adjustVisibleElements() {
  const container = document.querySelector('.recent-features');
  if(!container) return;
  const subElements = Array.from(container.children);

  let totalWidth = 0;

  subElements.forEach((element) => {
    totalWidth += 12 * parseFloat(getComputedStyle(document.documentElement).fontSize);

    if (totalWidth > window.innerWidth) {
      element.style.display = 'none';
    } else {
      element.style.display = 'block';
    }
  });
}

function adjustContainerAlignment() {
  document.querySelectorAll('.features-container').forEach((parent) => {
    parent.querySelectorAll('.feature-rows').forEach((container) => {
      const containerWidth = parent.offsetWidth;
      if (containerWidth < 32 * parseFloat(getComputedStyle(document.documentElement).fontSize)) {
        container.classList.add('single-column');
      } else {
        container.classList.remove('single-column');
      }
    });
  });
}

function toolsManager() {
  const convertToPDF = document.querySelector('#groupConvertTo');
  const convertFromPDF = document.querySelector('#groupConvertFrom');

  if (convertToPDF && convertFromPDF) {
    const itemsTo = Array.from(convertToPDF.querySelectorAll('.dropdown-item')).filter(
      (item) => !item.querySelector('hr.dropdown-divider')
    );
    const itemsFrom = Array.from(convertFromPDF.querySelectorAll('.dropdown-item')).filter(
      (item) => !item.querySelector('hr.dropdown-divider')
    );

    const totalItems = itemsTo.length + itemsFrom.length;

    if (totalItems > 12) {
      document.querySelectorAll('#convertGroup').forEach((element) => element.remove());
      document.querySelectorAll('#groupConvertTo').forEach((element) => (element.style.display = 'flex'));
      document.querySelectorAll('#groupConvertFrom').forEach((element) => (element.style.display = 'flex'));
    } else {
      document.querySelectorAll('#convertGroup').forEach((element) => (element.style.display = 'flex'));
      document.querySelectorAll('#groupConvertTo').forEach((element) => element.remove());
      document.querySelectorAll('#groupConvertFrom').forEach((element) => element.remove());
    }
  }
}

window.addEventListener('load', () => {
  adjustContainerAlignment();
  adjustVisibleElements();
});

window.addEventListener('resize', () => {
  adjustContainerAlignment();
  adjustVisibleElements();
});
